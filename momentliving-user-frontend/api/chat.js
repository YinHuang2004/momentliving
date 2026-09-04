import { get, post } from '../utils/request.js'

/**
 * 聊天模块接口层（网关前缀 /chat/**，chat-service）
 * 通道分工：发消息/推送走 WS（utils/websocket.js），这里全是 HTTP 查询与群管理命令
 */

/** 会话列表（单聊+群聊，含未读数/对端昵称头像/最后消息预览/canSend） */
export const getSessions = () => get('/api/chat/sessions')

/**
 * 历史消息（游标分页；重连补拉离线消息也用它）
 * @returns {list: ChatMessageVO[]（按 id 倒序，新→旧）, canSend: boolean}
 */
export const getMessages = ({ sessionId, cursor, size = 20 }) =>
  get('/api/chat/messages', { sessionId, cursor, size })

/** 创建/获取单聊会话（幂等），返回 ChatSessionVO {id, type, canSend, peerName...} */
export const ensureSingle = (peerUserId) => post('/api/chat/ensureSingle', { peerUserId })

/** 搜用户：昵称模糊 or 手机号精确 */
export const searchUsers = (keyword) => get('/api/chat/users/search', { keyword })

/** 标记会话已读（进入会话时调用） */
export const markRead = (sessionId) => post('/api/chat/read', { sessionId })

/** 未读消息总数（Tab 消息红点） */
export const unreadCount = () => get('/api/chat/unread')

/** 建群：创建者为群主，返回会话 VO */
export const createGroup = (groupName, memberIds) =>
  post('/api/chat/group/create', { groupName, memberIds })

/** 群成员列表（role 0成员 1管理 2群主） */
export const groupMembers = (groupId) => get(`/api/chat/group/${groupId}/members`)

/** 退群（群主不可退，只能解散） */
export const leaveGroup = (groupId) => post(`/api/chat/group/${groupId}/leave`)

/** 移除成员（群主/管理可操作） */
export const removeMember = (groupId, userId) => post(`/api/chat/group/${groupId}/remove`, { userId })

/** 设置管理员（仅群主） */
export const setAdmin = (groupId, userId) => post(`/api/chat/group/${groupId}/setAdmin`, { userId })

/** 解散群（仅群主） */
export const dissolveGroup = (groupId) => post(`/api/chat/group/${groupId}/dissolve`)
