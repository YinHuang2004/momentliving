package com.momentliving.controller;

import com.momentliving.context.UserHolder;
import com.momentliving.result.Result;
import com.momentliving.service.ChatService;
import com.momentliving.vo.ChatSessionVO;
import com.momentliving.vo.GroupMemberVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 聊天 REST 接口（查询 + 群管理命令）
 *
 * <p>分工铁律：发消息/推送走 WS（/ws/chat），这里只做会话列表、历史消息、
 * 搜用户、建会话、已读、未读数、群管理——全部经网关 X-User-Id 鉴权。
 */
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    /**
     * 会话列表（单聊+群聊，按最后消息时间倒序，含未读数/对端昵称头像/最后消息预览/canSend）
     */
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> sessions() {
        return Result.success(chatService.listSessions(currentUserId()));
    }

    /**
     * 历史消息（游标分页：cursor=上一批最旧一条的 id，首次不传取最新一批）
     * 响应带 canSend（首条限制由服务端算好，前端据此锁定输入框）；重连补拉离线消息也用它
     */
    @GetMapping("/messages")
    public Result<Map<String, Object>> messages(@RequestParam("sessionId") Long sessionId,
                                                @RequestParam(value = "cursor", required = false) Long cursor,
                                                @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(chatService.listMessages(currentUserId(), sessionId, cursor, size));
    }

    /**
     * 创建/获取单聊会话（幂等）；他人博客页"聊一聊"与搜索页进单聊都走它
     */
    @PostMapping("/ensureSingle")
    public Result<ChatSessionVO> ensureSingle(@RequestBody Map<String, Long> body) {
        Long peerUserId = body.get("peerUserId");
        return Result.success(chatService.ensureSingle(currentUserId(), peerUserId));
    }

    /**
     * 搜用户：昵称模糊 or 手机号精确（代理 user-service /user/feign/search）
     */
    @GetMapping("/users/search")
    public Result<List<UserVO>> searchUsers(@RequestParam("keyword") String keyword) {
        return Result.success(chatService.searchUsers(keyword));
    }

    /**
     * 标记会话已读（进入会话时调用；WS 在线时服务端还会回 read_ack）
     */
    @PostMapping("/read")
    public Result<Void> read(@RequestBody Map<String, Long> body) {
        chatService.markRead(currentUserId(), body.get("sessionId"));
        return Result.success();
    }

    /**
     * 未读消息总数（Tab 消息红点）
     */
    @GetMapping("/unread")
    public Result<Long> unread() {
        return Result.success(chatService.unreadCount(currentUserId()));
    }

    /**
     * 建群：{groupName, memberIds}，创建者为群主
     */
    @PostMapping("/group/create")
    public Result<ChatSessionVO> createGroup(@RequestBody Map<String, Object> body) {
        String groupName = (String) body.get("groupName");
        @SuppressWarnings("unchecked")
        List<Long> memberIds = ((List<Number>) body.getOrDefault("memberIds", List.of()))
                .stream().map(Number::longValue).toList();
        return Result.success(chatService.createGroup(currentUserId(), groupName, memberIds));
    }

    /**
     * 群成员列表（角色 0成员 1管理 2群主）
     */
    @GetMapping("/group/{id}/members")
    public Result<List<GroupMemberVO>> groupMembers(@PathVariable("id") Long groupId) {
        return Result.success(chatService.groupMembers(currentUserId(), groupId));
    }

    /** 退群（群主不可退，只能解散） */
    @PostMapping("/group/{id}/leave")
    public Result<Void> leaveGroup(@PathVariable("id") Long groupId) {
        chatService.leaveGroup(currentUserId(), groupId);
        return Result.success();
    }

    /** 移除成员：{userId}（群主/管理可操作） */
    @PostMapping("/group/{id}/remove")
    public Result<Void> removeMember(@PathVariable("id") Long groupId, @RequestBody Map<String, Long> body) {
        chatService.removeMember(currentUserId(), groupId, body.get("userId"));
        return Result.success();
    }

    /** 设置管理员：{userId}（仅群主） */
    @PostMapping("/group/{id}/setAdmin")
    public Result<Void> setAdmin(@PathVariable("id") Long groupId, @RequestBody Map<String, Long> body) {
        chatService.setAdmin(currentUserId(), groupId, body.get("userId"));
        return Result.success();
    }

    /** 解散群（仅群主） */
    @PostMapping("/group/{id}/dissolve")
    public Result<Void> dissolveGroup(@PathVariable("id") Long groupId) {
        chatService.dissolveGroup(currentUserId(), groupId);
        return Result.success();
    }

    private Long currentUserId() {
        UserVO user = UserHolder.getUser();
        if (user == null) {
            return null;
        }
        return user.getId();
    }
}
