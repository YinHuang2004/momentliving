import { transformUrl } from './urlTransform.js'

/**
 * 统一请求封装：所有页面通过它和后端通信
 * - 成功（HTTP 200 且 code===0）resolve 后端 data 字段；失败 toast + reject
 * - 登录态按角色命名空间隔离：用户端存 userToken（+ refreshToken/userInfo），
 *   商家端存 merchantToken（+ merchantInfo），两端互不顶号；
 *   发请求时按路径自动选 token：/api/merchant/** 用商家 token，其余用用户 token
 *   （商家接口已从 /admin 拆到 /merchant；/api/admin 属独立管理端 Web 工程，本前端不再调用）
 * - 401（msg 含"未登录/登录已过期"）：/api/merchant 开头跳商家登录页，否则跳用户登录页
 */
export function request({ url, method = 'GET', data = {}, header = {} }) {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync(tokenScopeOf(url) === 'merchant' ? 'merchantToken' : 'userToken')
    const finalHeader = { 'Content-Type': 'application/json', ...header }
    if (token) {
      finalHeader.Authorization = token
    }
    uni.request({
      url: transformUrl(url),
      method,
      data,
      header: finalHeader,
      success: (res) => {
        // 后端 Result 约定：code=1 成功 / 0 失败（文档写的 code=0 成功是错的）；
        // 兼容 success 布尔字段以防后续统一改造
        const ok = res.data && (res.data.code === 1 || res.data.success === true)
        if (res.statusCode === 200 && ok) {
          resolve(res.data.data)
          return
        }
        // 后端 Result 用 msg 字段；网关 401 响应体为 {success, message}，两者都兼容
        const msg = (res.data && (res.data.msg || res.data.message)) || '请求失败，请稍后再试'
        uni.showToast({ title: msg, icon: 'none' })
        if (msg.indexOf('未登录') >= 0 || msg.indexOf('登录已过期') >= 0 || res.statusCode === 401) {
          const isMerchant = isMerchantUrl(url)
          setTimeout(() => {
            uni.reLaunch({ url: isMerchant ? '/pages/merchant/login/login' : '/pages/user/login/login' })
          }, 600)
        }
        reject(new Error(msg))
      },
      fail: () => {
        uni.showToast({ title: '网络连接失败，请检查网络', icon: 'none' })
        reject(new Error('网络连接失败'))
      }
    })
  })
}

/** 便捷方法 */
export const get = (url, params = {}) => {
  // GET 参数拼到 query（后端部分接口用 @RequestParam 接收）
  const qs = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  return request({ url: qs ? `${url}?${qs}` : url, method: 'GET' })
}
export const post = (url, data = {}) => request({ url, method: 'POST', data })
export const put = (url, data = {}) => request({ url, method: 'PUT', data })
export const del = (url, data = {}) => request({ url, method: 'DELETE', data })

/**
 * 登录态工具（按角色命名空间隔离，用户/商家互不顶号）
 * - 用户端：userToken + refreshToken + userInfo
 * - 商家端：merchantToken + merchantInfo
 */

/** 商家态路径：/api/merchant/** 与商家专属的 AI 接口 /api/ai/merchant/** */
function isMerchantUrl(url) {
  return typeof url === 'string' &&
    (url.startsWith('/api/merchant') || url.startsWith('/api/ai/merchant'))
}

/** 按请求路径判定登录态命名空间：商家端路径用 merchant token，其余为用户端 */
export function tokenScopeOf(url) {
  return isMerchantUrl(url) ? 'merchant' : 'user'
}

export const setUserToken = (token) => uni.setStorageSync('userToken', token)
export const setMerchantToken = (token) => uni.setStorageSync('merchantToken', token)

/** 用户端 token（websocket 握手、file 上传等固定用户态场景用） */
export const getToken = () => uni.getStorageSync('userToken')
/** 按命名空间取 token：request.js 内部按路径选 key 用 */
export const getTokenByScope = (scope) => uni.getStorageSync(scope === 'merchant' ? 'merchantToken' : 'userToken')

/** 用户退出：清用户端 token + refreshToken（userInfo 由页面自行清理） */
export const clearToken = () => {
  uni.removeStorageSync('userToken')
  uni.removeStorageSync('refreshToken')
}
/** 商家退出：只清商家端，不影响用户登录态 */
export const clearMerchantToken = () => uni.removeStorageSync('merchantToken')

/** @deprecated 兼容旧引用，等价于 setUserToken */
export const setToken = setUserToken
