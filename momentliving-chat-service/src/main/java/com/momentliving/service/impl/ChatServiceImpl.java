package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.momentliving.api.client.UserClient;
import com.momentliving.entity.ChatGroup;
import com.momentliving.entity.ChatGroupMember;
import com.momentliving.entity.ChatMessage;
import com.momentliving.entity.ChatSession;
import com.momentliving.mapper.ChatGroupMapper;
import com.momentliving.mapper.ChatGroupMemberMapper;
import com.momentliving.mapper.ChatMessageMapper;
import com.momentliving.mapper.ChatSessionMapper;
import com.momentliving.result.Result;
import com.momentliving.service.ChatService;
import com.momentliving.service.MessagePushService;
import com.momentliving.vo.ChatMessageVO;
import com.momentliving.vo.ChatSessionVO;
import com.momentliving.vo.GroupMemberVO;
import com.momentliving.vo.UserVO;
import com.momentliving.ws.ChatRejectException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private ChatSessionMapper chatSessionMapper;
    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private ChatGroupMapper chatGroupMapper;
    @Resource
    private ChatGroupMemberMapper chatGroupMemberMapper;
    @Resource
    private MessagePushService pushService;
    @Resource
    private UserClient userClient;

    // ==================== WS 发送链路 ====================

    @Override
    public void send(Long senderId, JsonNode node) {
        Long sessionId = node.path("sessionId").asLong();
        int type = node.path("type").asInt(ChatMessage.TYPE_TEXT);
        String content = node.path("content").asText("");
        String clientMsgId = node.path("clientMsgId").asText(null);
        if (clientMsgId == null || clientMsgId.isBlank()) {
            clientMsgId = senderId + "-" + System.nanoTime();   // 客户端没带就兜底生成（无幂等效果，仅作主键）
        }

        try {
            ChatMessage saved = doSend(senderId, sessionId, type, content, clientMsgId);
            // 推送给接收方/群成员（在线才推，离线已落库等补拉）
            pushService.pushToTargets(saved, Map.of("op", "new_msg", "msg", toVO(saved)));
            // ack 给发送者：前端把"发送中"气泡变成"已发送"；canSend 让发起方发完首条立即锁输入框，
            // 不必等第二条被 reject 才发现被锁
            ChatSession fresh = chatSessionMapper.selectById(sessionId);
            pushService.pushToUser(senderId, Map.of(
                    "op", "ack", "clientMsgId", clientMsgId, "msgId", saved.getId(), "ok", true,
                    "canSend", fresh != null && canSend(fresh, senderId)));
        } catch (DuplicateKeyException e) {
            // 幂等：断线重发导致 uk_client_msg 撞唯一索引，查回原消息按成功 ack（静默去重）
            ChatMessage exist = chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getClientMsgId, clientMsgId).last("limit 1"));
            if (exist != null) {
                pushService.pushToUser(senderId, Map.of(
                        "op", "ack", "clientMsgId", clientMsgId, "msgId", exist.getId(), "ok", true));
            }
        } catch (ChatRejectException e) {
            pushService.pushToUser(senderId, Map.of(
                    "op", "reject", "clientMsgId", clientMsgId, "code", e.getCode(), "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("WS 消息处理失败 senderId={}", senderId, e);
            pushService.pushToUser(senderId, Map.of(
                    "op", "reject", "clientMsgId", clientMsgId, "code", ChatRejectException.ERROR, "msg", "发送失败，请重试"));
        }
    }

    /**
     * 成员校验 → 首条限制状态机 → 落库 → 刷新会话预览。
     * 故意不加 @Transactional：send() 内部自调用时事务注解本就不生效（代理不拦截 this 调用），
     * 且这里只有 insert + touchLastMessage 两条写语句——touch 失败只会让预览短暂过期，无害。
     * 先落库后推送：推送失败不影响消息到达（离线补拉兜底）。
     */
    protected ChatMessage doSend(Long senderId, Long sessionId, int type, String content, String clientMsgId) {
        ChatSession cs = chatSessionMapper.selectById(sessionId);
        if (cs == null) {
            throw new ChatRejectException(ChatRejectException.NO_SESSION, "会话不存在");
        }
        checkMember(cs, senderId);

        // ★ 首条限制三态状态机（仅单聊）：
        //   INIT(0)：谁发首条谁就是发起方 → CAS 置 WAIT_REPLY(2) 并落 initiator_id（放行这一条）
        //   WAIT_REPLY(2)：发起方再发 → 拒绝 WAIT_REPLY；接收方回复 → CAS 置 FREE(1)
        //   FREE(1)：双方自由
        // 发起方一律以"首条消息的真实发送者"为准，不信表里的 initiator_id——
        // 旧数据迁移时它是按 user_a_id（两端较小ID）猜的，可能猜错导致限制失效（防骚扰失灵根因）。
        // INIT 状态没有任何消息，当前发送者就是发起方；WAIT_REPLY 状态查首条消息修正并判定。
        if (cs.getType() == ChatSession.TYPE_SINGLE && cs.getFirstReply() != ChatSession.FR_FREE) {
            int fr = cs.getFirstReply() == null ? ChatSession.FR_INIT : cs.getFirstReply();
            if (fr == ChatSession.FR_INIT) {
                // 首条：CAS 推进状态并落真实发起方（0 行 = 已被并发请求推进，按 WAIT_REPLY 拒绝）
                if (chatSessionMapper.markInitiatorSent(sessionId, senderId) == 0) {
                    throw new ChatRejectException(ChatRejectException.WAIT_REPLY, "对方回复前仅可发送一条");
                }
            } else {
                // WAIT_REPLY：发起方在对方回复前再发 → 拒绝；接收方回复 → 解除限制
                Long realInitiator = chatMessageMapper.selectFirstSender(sessionId);
                if (realInitiator == null) {
                    // 状态是 WAIT_REPLY 却查不到消息（脏数据）：按 INIT 处理，重新锁到当前发送者
                    if (chatSessionMapper.markInitiatorSent(sessionId, senderId) == 0) {
                        throw new ChatRejectException(ChatRejectException.WAIT_REPLY, "对方回复前仅可发送一条");
                    }
                } else {
                    // 表中 initiator_id 与首条消息发送者不一致时回写修正，canSend 判定随之恢复正确
                    if (!realInitiator.equals(cs.getInitiatorId())) {
                        chatSessionMapper.updateInitiator(sessionId, realInitiator);
                    }
                    if (realInitiator.equals(senderId)) {
                        throw new ChatRejectException(ChatRejectException.WAIT_REPLY, "对方回复前仅可发送一条");
                    }
                    chatSessionMapper.markFirstReplied(sessionId);
                }
            }
        }

        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .senderId(senderId)
                .receiverId(cs.getType() == ChatSession.TYPE_SINGLE ? theOtherOf(cs, senderId) : 0L)
                .type(type)
                .content(content)
                .isRead(0)
                .clientMsgId(clientMsgId)
                .build();
        try {
            chatMessageMapper.insert(msg);
        } catch (DuplicateKeyException e) {
            throw e;    // 交给上层按幂等处理
        }
        chatSessionMapper.touchLastMessage(sessionId, previewOf(type, content));
        return msg;
    }

    @Override
    public void markRead(Long userId, Long sessionId) {
        // 只把"对方发给我的"置为已读，自己发的消息不存在已读一说
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getIsRead, 0)
                .ne(ChatMessage::getSenderId, userId)
                .set(ChatMessage::getIsRead, 1));
        pushService.pushToUser(userId, Map.of("op", "read_ack", "sessionId", sessionId));
    }

    // ==================== HTTP 查询 ====================

    @Override
    public List<ChatSessionVO> listSessions(Long userId) {
        // 我参与的单聊
        List<ChatSession> singles = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getType, ChatSession.TYPE_SINGLE)
                .and(w -> w.eq(ChatSession::getUserAId, userId).or().eq(ChatSession::getUserBId, userId)));
        // 我参与的群聊
        List<Long> myGroupIds = chatGroupMemberMapper.selectList(new LambdaQueryWrapper<ChatGroupMember>()
                        .eq(ChatGroupMember::getUserId, userId))
                .stream().map(ChatGroupMember::getGroupId).toList();
        List<ChatSession> groups = myGroupIds.isEmpty() ? List.of()
                : chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getType, ChatSession.TYPE_GROUP)
                        .in(ChatSession::getGroupId, myGroupIds));

        List<ChatSession> all = new ArrayList<>(singles);
        all.addAll(groups);
        if (all.isEmpty()) {
            return List.of();
        }
        // 按最后消息时间倒序（空的排最后）
        all.sort(Comparator.comparing(ChatSession::getLastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // 一次聚合查询各会话未读数（避免 N+1）
        List<Long> sessionIds = all.stream().map(ChatSession::getId).toList();
        Map<Long, Long> unreadMap = countUnreadBySession(userId, sessionIds);

        // 批量回填单聊对端昵称头像（昵称/头像一律来自 user-service，chat 库不冗余用户表）
        Map<Long, UserVO> userMap = fetchUsers(all.stream()
                .filter(s -> s.getType() == ChatSession.TYPE_SINGLE)
                .map(s -> userId.equals(s.getUserAId()) ? s.getUserBId() : s.getUserAId())
                .toList());
        // 批量取群名
        List<Long> gids = all.stream().map(ChatSession::getGroupId).filter(g -> g != null).toList();
        Map<Long, ChatGroup> groupMap = gids.isEmpty() ? Map.of()
                : chatGroupMapper.selectBatchIds(gids).stream()
                        .collect(Collectors.toMap(ChatGroup::getId, g -> g, (a, b) -> a));

        return all.stream().map(s -> {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setId(s.getId());
            vo.setType(s.getType());
            vo.setLastMessage(s.getLastMessage());
            vo.setLastMessageAt(s.getLastMessageAt());
            vo.setUnreadCount(unreadMap.getOrDefault(s.getId(), 0L));
            vo.setCanSend(canSend(s, userId));
            if (s.getType() == ChatSession.TYPE_SINGLE) {
                Long peerId = userId.equals(s.getUserAId()) ? s.getUserBId() : s.getUserAId();
                UserVO peer = userMap.get(peerId);
                vo.setPeerId(peerId);
                vo.setPeerName(peer != null ? peer.getNickName() : "用户" + peerId);
                vo.setPeerAvatar(peer != null ? peer.getImages() : null);
            } else {
                ChatGroup g = groupMap.get(s.getGroupId());
                vo.setGroupId(s.getGroupId());
                vo.setGroupName(g != null ? g.getGroupName() : "群聊");
            }
            return vo;
        }).toList();
    }

    @Override
    public Map<String, Object> listMessages(Long userId, Long sessionId, Long cursor, int size) {
        ChatSession cs = requireMember(sessionId, userId);
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId);
        if (cursor != null && cursor > 0) {
            qw.lt(ChatMessage::getId, cursor);   // 游标分页：翻更早的消息
        }
        qw.orderByDesc(ChatMessage::getId).last("limit " + Math.min(Math.max(size, 1), 50));
        List<ChatMessage> messages = chatMessageMapper.selectList(qw);
        List<ChatMessageVO> vos = toVOs(messages);
        return Map.of("list", vos, "canSend", canSend(cs, userId));
    }

    @Override
    public ChatSessionVO ensureSingle(Long userId, Long peerUserId) {
        if (peerUserId == null || peerUserId <= 0) {
            throw new ChatRejectException(ChatRejectException.ERROR, "缺少对方用户ID");
        }
        if (peerUserId.equals(userId)) {
            throw new ChatRejectException(ChatRejectException.ERROR, "不能和自己聊天");
        }
        // 归一化：user_a_id 恒存较小ID，A 找 B 与 B 找 A 命中同一行
        long a = Math.min(userId, peerUserId);
        long b = Math.max(userId, peerUserId);
        ChatSession existing = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserAId, a).eq(ChatSession::getUserBId, b)
                .eq(ChatSession::getType, ChatSession.TYPE_SINGLE));
        if (existing == null) {
            ChatSession cs = ChatSession.builder()
                    .type(ChatSession.TYPE_SINGLE).userAId(a).userBId(b)
                    .initiatorId(userId)        // ★ 真实发起方 = 调用 ensureSingle 的人（与归一化的 user_a_id 无关）
                    .firstReply(ChatSession.FR_INIT).build();
            try {
                chatSessionMapper.insert(cs);
                existing = cs;
            } catch (DuplicateKeyException e) {
                // 并发点"聊一聊"：撞 uk_single 唯一索引 → 回查既有会话（幂等）
                existing = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserAId, a).eq(ChatSession::getUserBId, b)
                        .eq(ChatSession::getType, ChatSession.TYPE_SINGLE));
            }
        }
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(existing.getId());
        vo.setType(existing.getType());
        vo.setPeerId(peerUserId);
        vo.setCanSend(canSend(existing, userId));
        Map<Long, UserVO> userMap = fetchUsers(List.of(peerUserId));
        UserVO peer = userMap.get(peerUserId);
        vo.setPeerName(peer != null ? peer.getNickName() : "用户" + peerUserId);
        vo.setPeerAvatar(peer != null ? peer.getImages() : null);
        return vo;
    }

    @Override
    public List<UserVO> searchUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        Result<List<UserVO>> res = userClient.searchUsers(keyword.trim());
        return res == null || res.getData() == null ? List.of() : res.getData();
    }

    @Override
    public long unreadCount(Long userId) {
        List<Long> sessionIds = mySessionIds(userId);
        if (sessionIds.isEmpty()) {
            return 0;
        }
        return chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .in(ChatMessage::getSessionId, sessionIds)
                .eq(ChatMessage::getIsRead, 0)
                .ne(ChatMessage::getSenderId, userId));
    }

    // ==================== 群聊 ====================

    @Override
    @Transactional
    public ChatSessionVO createGroup(Long userId, String groupName, List<Long> memberIds) {
        if (groupName == null || groupName.isBlank()) {
            throw new ChatRejectException(ChatRejectException.ERROR, "请填写群名称");
        }
        // 成员去重 + 去掉自己（自己以群主身份入群）
        List<Long> others = memberIds == null ? List.of()
                : memberIds.stream().filter(id -> id != null && id > 0 && !id.equals(userId)).distinct().toList();

        ChatGroup group = ChatGroup.builder()
                .groupName(groupName.trim())
                .ownerId(userId)
                .memberCount(others.size() + 1)
                .build();
        chatGroupMapper.insert(group);

        ChatGroupMember owner = ChatGroupMember.builder()
                .groupId(group.getId()).userId(userId).role(ChatGroupMember.ROLE_OWNER).build();
        chatGroupMemberMapper.insert(owner);
        for (Long id : others) {
            chatGroupMemberMapper.insert(ChatGroupMember.builder()
                    .groupId(group.getId()).userId(id).role(ChatGroupMember.ROLE_MEMBER).build());
        }

        ChatSession cs = ChatSession.builder()
                .type(ChatSession.TYPE_GROUP).userAId(userId).groupId(group.getId())
                .firstReply(1)      // 群聊不做首条限制
                .lastMessageAt(LocalDateTime.now())
                .build();
        chatSessionMapper.insert(cs);

        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(cs.getId());
        vo.setType(ChatSession.TYPE_GROUP);
        vo.setGroupId(group.getId());
        vo.setGroupName(group.getGroupName());
        vo.setCanSend(true);
        return vo;
    }

    @Override
    public List<GroupMemberVO> groupMembers(Long userId, Long groupId) {
        requireGroupMember(groupId, userId);
        List<ChatGroupMember> members = chatGroupMemberMapper.selectList(
                new LambdaQueryWrapper<ChatGroupMember>().eq(ChatGroupMember::getGroupId, groupId));
        Map<Long, UserVO> userMap = fetchUsers(members.stream().map(ChatGroupMember::getUserId).toList());
        return members.stream().map(m -> {
            GroupMemberVO vo = new GroupMemberVO();
            vo.setUserId(m.getUserId());
            vo.setGroupNickname(m.getGroupNickname());
            vo.setRole(m.getRole());
            vo.setJoinTime(m.getJoinTime());
            UserVO u = userMap.get(m.getUserId());
            vo.setNickName(u != null ? u.getNickName() : "用户" + m.getUserId());
            vo.setImages(u != null ? u.getImages() : null);
            return vo;
        // 群主在前，其次管理，其余按入群时间
        }).sorted(Comparator.comparing((GroupMemberVO v) -> v.getRole()).reversed()
                .thenComparing(GroupMemberVO::getJoinTime))
                .toList();
    }

    @Override
    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        ChatGroup group = requireGroupMember(groupId, userId);
        if (group.getOwnerId().equals(userId)) {
            throw new ChatRejectException(ChatRejectException.NO_PERMISSION, "群主不能退出，请解散群聊");
        }
        removeMemberRow(groupId, userId);
    }

    @Override
    @Transactional
    public void removeMember(Long userId, Long groupId, Long targetUserId) {
        ChatGroup group = requireGroupMember(groupId, userId);
        ChatGroupMember actor = memberRow(groupId, userId);
        boolean isOwner = group.getOwnerId().equals(userId);
        boolean isAdmin = actor != null && actor.getRole() == ChatGroupMember.ROLE_ADMIN;
        if (!isOwner && !isAdmin) {
            throw new ChatRejectException(ChatRejectException.NO_PERMISSION, "仅群主或管理员可移除成员");
        }
        if (group.getOwnerId().equals(targetUserId)) {
            throw new ChatRejectException(ChatRejectException.NO_PERMISSION, "不能移除群主");
        }
        removeMemberRow(groupId, targetUserId);
    }

    @Override
    public void setAdmin(Long userId, Long groupId, Long targetUserId) {
        ChatGroup group = requireGroupMember(groupId, userId);
        if (!group.getOwnerId().equals(userId)) {
            throw new ChatRejectException(ChatRejectException.NO_PERMISSION, "仅群主可设置管理员");
        }
        ChatGroupMember target = memberRow(groupId, targetUserId);
        if (target == null) {
            throw new ChatRejectException(ChatRejectException.NO_SESSION, "对方不是群成员");
        }
        target.setRole(ChatGroupMember.ROLE_ADMIN);
        chatGroupMemberMapper.updateById(target);
    }

    @Override
    @Transactional
    public void dissolveGroup(Long userId, Long groupId) {
        ChatGroup group = requireGroupMember(groupId, userId);
        if (!group.getOwnerId().equals(userId)) {
            throw new ChatRejectException(ChatRejectException.NO_PERMISSION, "仅群主可解散群聊");
        }
        chatGroupMemberMapper.delete(new LambdaQueryWrapper<ChatGroupMember>()
                .eq(ChatGroupMember::getGroupId, groupId));
        chatGroupMapper.deleteById(groupId);
        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getGroupId, groupId));
    }

    // ==================== 私有工具 ====================

    /** 校验 cs 是单聊/群聊会话且 userId 是成员，否则抛 ChatRejectException */
    private void checkMember(ChatSession cs, Long userId) {
        if (cs.getType() == ChatSession.TYPE_SINGLE) {
            if (!userId.equals(cs.getUserAId()) && !userId.equals(cs.getUserBId())) {
                throw new ChatRejectException(ChatRejectException.NO_PERMISSION, "你不在该会话中");
            }
        } else {
            requireGroupMember(cs.getGroupId(), userId);
        }
    }

    /** 校验 userId 是群成员并返回群信息 */
    private ChatGroup requireGroupMember(Long groupId, Long userId) {
        ChatGroup group = chatGroupMapper.selectById(groupId);
        if (group == null) {
            throw new ChatRejectException(ChatRejectException.NO_SESSION, "群聊不存在或已解散");
        }
        if (memberRow(groupId, userId) == null) {
            throw new ChatRejectException(ChatRejectException.NO_PERMISSION, "你不是该群成员");
        }
        return group;
    }

    private ChatSession requireMember(Long sessionId, Long userId) {
        ChatSession cs = chatSessionMapper.selectById(sessionId);
        if (cs == null) {
            throw new ChatRejectException(ChatRejectException.NO_SESSION, "会话不存在");
        }
        checkMember(cs, userId);
        return cs;
    }

    private ChatGroupMember memberRow(Long groupId, Long userId) {
        return chatGroupMemberMapper.selectOne(new LambdaQueryWrapper<ChatGroupMember>()
                .eq(ChatGroupMember::getGroupId, groupId).eq(ChatGroupMember::getUserId, userId));
    }

    private void removeMemberRow(Long groupId, Long targetUserId) {
        chatGroupMemberMapper.delete(new LambdaQueryWrapper<ChatGroupMember>()
                .eq(ChatGroupMember::getGroupId, groupId).eq(ChatGroupMember::getUserId, targetUserId));
        chatGroupMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatGroup>()
                .eq(ChatGroup::getId, groupId)
                .setSql("member_count = member_count - 1"));
    }

    /** 首条限制的 canSend 判定：WAIT_REPLY 状态下仅发起方被锁，其余状态双方可发（规则判定在服务端，前端只执行） */
    private boolean canSend(ChatSession cs, Long userId) {
        if (cs.getType() != ChatSession.TYPE_SINGLE) {
            return true;
        }
        int fr = cs.getFirstReply() == null ? ChatSession.FR_INIT : cs.getFirstReply();
        if (fr == ChatSession.FR_FREE || fr == ChatSession.FR_INIT) {
            return true;
        }
        // WAIT_REPLY：发起方等回复中，仅对方可发。
        // initiator_id 异常（null/0，旧脏数据）时不锁任何人，等 doSend 按首条消息自愈修正
        return cs.getInitiatorId() == null || cs.getInitiatorId() <= 0
                || !userId.equals(cs.getInitiatorId());
    }

    private Long theOtherOf(ChatSession cs, Long userId) {
        return userId.equals(cs.getUserAId()) ? cs.getUserBId() : cs.getUserAId();
    }

    /** 会话列表预览文案：文本截断 / [图片] / [博客] */
    private String previewOf(int type, String content) {
        String preview = switch (type) {
            case ChatMessage.TYPE_IMAGE -> "[图片]";
            case ChatMessage.TYPE_BLOG_CARD -> "[博客]";
            default -> content;
        };
        return preview.length() > 100 ? preview.substring(0, 100) : preview;
    }

    /** 我参与的全部会话ID（单聊 + 群聊） */
    private List<Long> mySessionIds(Long userId) {
        List<Long> ids = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .select(ChatSession::getId)
                        .eq(ChatSession::getType, ChatSession.TYPE_SINGLE)
                        .and(w -> w.eq(ChatSession::getUserAId, userId).or().eq(ChatSession::getUserBId, userId)))
                .stream().map(ChatSession::getId).collect(Collectors.toList());
        List<Long> myGroupIds = chatGroupMemberMapper.selectList(new LambdaQueryWrapper<ChatGroupMember>()
                        .select(ChatGroupMember::getGroupId)
                        .eq(ChatGroupMember::getUserId, userId))
                .stream().map(ChatGroupMember::getGroupId).toList();
        if (!myGroupIds.isEmpty()) {
            chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                            .select(ChatSession::getId)
                            .eq(ChatSession::getType, ChatSession.TYPE_GROUP)
                            .in(ChatSession::getGroupId, myGroupIds))
                    .forEach(s -> ids.add(s.getId()));
        }
        return ids;
    }

    /** 各会话未读数（一次 group by 聚合，避免 N+1） */
    private Map<Long, Long> countUnreadBySession(Long userId, List<Long> sessionIds) {
        QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
        qw.select("session_id", "count(*) as cnt")
                .eq("is_read", 0)
                .ne("sender_id", userId)
                .in("session_id", sessionIds)
                .groupBy("session_id");
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> row : chatMessageMapper.selectMaps(qw)) {
            Object sid = row.get("session_id");
            Object cnt = row.get("cnt");
            if (sid != null && cnt != null) {
                map.put(Long.valueOf(sid.toString()), Long.valueOf(cnt.toString()));
            }
        }
        return map;
    }

    /** 批量取用户资料（一次 Feign；Feign 接口要求非空列表） */
    private Map<Long, UserVO> fetchUsers(Collection<Long> ids) {
        List<Long> distinct = ids == null ? List.of()
                : ids.stream().filter(id -> id != null).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Result<List<UserVO>> res = userClient.selectUsersByIdsOrdered(new ArrayList<>(distinct));
        List<UserVO> list = res == null || res.getData() == null ? List.of() : res.getData();
        return list.stream()
                .filter(u -> u != null && u.getId() != null)
                .collect(Collectors.toMap(UserVO::getId, u -> u, (a, b) -> a, TreeMap::new));
    }

    /** 实体 → VO 并回填发送者昵称头像（历史消息列表走 toVOs 批量，避免 N+1） */
    private ChatMessageVO toVO(ChatMessage msg) {
        return toVOs(List.of(msg)).get(0);
    }

    /** 批量实体 → VO（所有发送者一次 Feign 聚合，历史消息一页 20 条只调一次用户服务） */
    private List<ChatMessageVO> toVOs(Collection<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, UserVO> userMap = fetchUsers(messages.stream()
                .map(ChatMessage::getSenderId).toList());
        return messages.stream().map(msg -> {
            ChatMessageVO vo = new ChatMessageVO();
            vo.setId(msg.getId());
            vo.setSessionId(msg.getSessionId());
            vo.setSenderId(msg.getSenderId());
            vo.setType(msg.getType());
            vo.setContent(msg.getContent());
            vo.setCreateTime(msg.getCreateTime());
            UserVO u = userMap.get(msg.getSenderId());
            if (u != null) {
                vo.setSenderName(u.getNickName());
                vo.setSenderAvatar(u.getImages());
            }
            return vo;
        }).toList();
    }
}
