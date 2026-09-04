// 网关地址：H5 走 vite 代理（vite.config.js），App/小程序直连这里（改成实际部署 IP）
// 导出供 utils/websocket.js 复用（http:// → ws:// 直连网关，WS 不过 vite 代理）
export const BASE_URL = 'http://localhost:8080'

/**
 * URL 转换：页面/api 层统一写 /api/xxx，这里负责适配
 * - H5：不动（交给 vite proxy，rewrite 去掉 /api 前缀）
 * - App/小程序：去掉 /api 前缀，拼网关地址（momentliving 网关无 /gateway 前缀）
 */
export function transformUrl(originalUrl) {
  let url = originalUrl
  // #ifndef H5
  if (url.startsWith('/api')) {
    url = url.replace(/^\/api/, '')
  }
  url = BASE_URL + url
  // #endif
  return url
}
