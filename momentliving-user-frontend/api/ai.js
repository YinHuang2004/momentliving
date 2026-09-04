import { get, post, request } from '../utils/request.js'

/**
 * AI 助手接口层（网关前缀 /ai/**，ai-service）
 * 对话优先走 SSE 流式（H5 fetch 流），不支持流式的端用 chat() 同步接口
 */

/** 同步对话（返回完整回复 AiMessageVO） */
export const chat = (message, conversationId) =>
  post('/api/ai/chat', { message, conversationId })

/** 会话列表 */
export const conversations = () => get('/api/ai/conversations')

/** 新建会话 */
export const createConversation = () => post('/api/ai/conversations')

/** 删除会话（连同历史消息） */
export const deleteConversation = (id) =>
  request({ url: `/api/ai/conversations/${id}`, method: 'DELETE' })

/** 会话历史消息 */
export const conversationMessages = (id) => get(`/api/ai/conversations/${id}/messages`)

/** 回答反馈（rating 1-5） */
export const feedback = (messageId, rating, comment) =>
  post('/api/ai/feedback', { messageId, rating, comment })

/** 商铺推荐（偏好描述 → 店铺 + AI 推荐语） */
export const recommendShop = (preference, area) =>
  post('/api/ai/recommend/shop', { preference, area })

/** 探店博客草稿生成 */
export const generateBlog = (shopId, style, keywords) =>
  post('/api/ai/generate/blog', { shopId, style, keywords })

/** 评价文案生成 */
export const generateReview = (shopId, rating, impression) =>
  post('/api/ai/generate/review', { shopId, rating, impression })

/** 商家端 AI 接口（/api/ai/merchant/**，request 层自动选商家 token） */
export const merchantAnalysis = () => post('/api/ai/merchant/analysis')

/** 商家端：优惠券营销文案生成 */
export const merchantCopywriting = (voucherDesc, sellingPoint) =>
  post('/api/ai/merchant/copywriting', { voucherDesc, sellingPoint })

/** 商家端：店铺介绍优化 */
export const merchantShopIntro = () => post('/api/ai/merchant/shop-intro')

/**
 * SSE 流式对话（H5 专用：uni.request 不支持流式，App/小程序请用 chat()）。
 * 回调：onMeta(conversationId) / onChunk(text) / onDone() / onError(msg)
 */
export async function chatStream(message, conversationId, { onMeta, onChunk, onDone, onError } = {}) {
  // #ifdef H5
  const token = uni.getStorageSync('userToken')
  const { transformUrl } = await import('../utils/urlTransform.js')
  const qs = `message=${encodeURIComponent(message)}` +
    (conversationId ? `&conversationId=${conversationId}` : '')
  const resp = await fetch(`${transformUrl('/api/ai/chat/stream')}?${qs}`, {
    headers: token ? { Authorization: token } : {}
  })
  if (!resp.ok || !resp.body) {
    onError && onError('AI 服务暂时不可用，请稍后重试')
    return
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  const handleEvent = (raw) => {
    let eventName = 'message'
    const dataLines = []
    raw.split('\n').forEach((line) => {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
    })
    if (!dataLines.length) return
    const data = dataLines.join('\n')
    if (eventName === 'meta') {
      try { onMeta && onMeta(JSON.parse(data).conversationId) } catch (e) { /* 忽略 */ }
    } else if (eventName === 'done') {
      onDone && onDone()
    } else if (eventName === 'error') {
      onError && onError(data)
    } else {
      onChunk && onChunk(data)
    }
  }
  // 手动按空行分包（SSE 规范：事件间以空行分隔）
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const raw = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      if (raw.trim()) handleEvent(raw)
    }
  }
  onDone && onDone()
  // #endif

  // #ifndef H5
  // App/小程序无流式能力，降级同步对话
  try {
    const res = await chat(message, conversationId)
    onMeta && onMeta(res.conversationId)
    onChunk && onChunk(res.content)
    onDone && onDone()
  } catch (e) {
    onError && onError(e.message || 'AI 服务暂时不可用')
  }
  // #endif
}
