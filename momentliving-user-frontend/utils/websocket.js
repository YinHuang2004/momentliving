import { BASE_URL } from './urlTransform.js'

/**
 * WS 单例客户端：连接 / 握手鉴权 / 心跳 / 断线重连 / 消息分发
 *
 * - 连接地址：ws://网关:8080/ws/chat?token=xxx（token 走 query，浏览器原生 WS 不能自定义头）
 * - 注意：WS 直连网关，不过 vite 的 /api 代理（那套代理只处理 HTTP 且带 bypass）
 * - 协议：客户端 {op:'send'|'read'|'ping'}，服务端 {op:'new_msg'|'ack'|'reject'|'read_ack'|'pong'}
 *   详见《21 WebSocket聊天功能技术文档》4.4 节
 * - ★ 连接与 token 绑定：发送者身份由服务端从握手 token 解出。换账号（登出后再登录）
 *   若复用旧账号的连接，新账号发的消息会被服务端记到旧账号头上（气泡左右颠倒、
 *   首条限制锁错人），所以 connectWS 检测到 token 变化就主动重建连接
 */
const WS_BASE = BASE_URL.replace(/^http/, 'ws')

let task = null              // ★ 必须持有 uni.connectSocket 返回的 SocketTask 引用：
                             //   不持有会被 GC 回收，表现为"连接莫名其妙断开"
let connToken = null         // 当前连接握手所用的 token（身份绑定）
let heartbeatTimer = null
let retry = 0                // 重连次数（指数退避）
let manualClosed = false     // 主动关闭（登出）时不再重连
const listeners = new Set()  // 页面级订阅者：onWSMessage(fn) 注册，返回取消订阅函数

export function connectWS() {
  // 聊天 WS 是用户态功能，固定用用户端 token
  const token = uni.getStorageSync('userToken')
  if (!token) return
  if (task) {
    if (connToken === token) return
    // 换账号了：作废旧连接再重建。onClose 里用 task === sock 判定，
    // 旧连接迟到的 onClose 不会误清下面新建的连接
    const old = task
    task = null
    stopHeartbeat()
    try {
      old.close({ code: 1000 })
    } catch (e) {
      // 忽略
    }
  }
  manualClosed = false
  connToken = token
  const sock = uni.connectSocket({
    url: `${WS_BASE}/ws/chat?token=${encodeURIComponent(token)}`,
    complete: () => {}
  })
  task = sock
  sock.onOpen(() => {
    if (task !== sock) return
    retry = 0
    startHeartbeat()
  })
  sock.onMessage((res) => {
    let msg
    try {
      msg = JSON.parse(res.data)
    } catch (e) {
      return
    }
    listeners.forEach((fn) => {
      try {
        fn(msg)
      } catch (e) {
        console.error('WS 监听器执行异常', e)
      }
    })
  })
  sock.onClose(() => {
    if (task !== sock) return   // 已被新连接顶替，不清理新连接的状态
    task = null
    connToken = null
    stopHeartbeat()
    scheduleReconnect()
  })
  sock.onError(() => {
    // onClose 在错误后也会触发，重连统一在 onClose 调度，这里不重复
  })
}

/** 发送一帧（op 协议 JSON） */
export function sendWS(payload) {
  if (task) {
    task.send({ data: JSON.stringify(payload) })
  }
}

/** 订阅服务端帧；返回取消订阅函数（页面 onUnload 时调用防泄漏） */
export function onWSMessage(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

export function isConnected() {
  return !!task
}

/** 主动关闭（退出登录时调用），不再自动重连 */
export function closeWS() {
  manualClosed = true
  stopHeartbeat()
  const sock = task
  task = null
  connToken = null
  if (sock) {
    try {
      sock.close({ code: 1000 })
    } catch (e) {
      // 忽略
    }
  }
}

// ---------------- 内部：心跳与重连 ----------------

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => sendWS({ op: 'ping' }), 30000)   // 30s 心跳防闲置断连
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

/** 指数退避重连：1s/2s/4s…封顶 30s；token 没了（登出）就停 */
function scheduleReconnect() {
  if (manualClosed || !uni.getStorageSync('userToken')) return
  const delay = Math.min(1000 * 2 ** retry++, 30000)
  setTimeout(() => {
    if (!task) connectWS()
  }, delay)
}
