import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * 统一 axios 封装：
 * - baseURL /api（vite 代理到网关 8080，生产由 nginx 反代）
 * - 自动带 Authorization（管理端 token，localStorage 'adminToken'）
 * - 后端 Result 约定：code=1 成功 resolve data；失败 toast msg 后 reject
 * - 401：清登录态跳 /login
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('adminToken')
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const body = res.data
    const ok = body && (body.code === 1 || body.success === true)
    if (ok) {
      return body.data
    }
    const msg = (body && (body.msg || body.message)) || '请求失败，请稍后再试'
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  (err) => {
    const status = err.response && err.response.status
    const body = err.response && err.response.data
    const msg = (body && (body.msg || body.message)) || '网络异常，请稍后再试'
    ElMessage.error(msg)
    if (status === 401) {
      localStorage.removeItem('adminToken')
      localStorage.removeItem('adminInfo')
      router.push('/login')
    }
    return Promise.reject(err)
  }
)

/**
 * 图片上传（店铺图/券图）：POST /file/upload?dir=xxx，multipart
 * @returns {Promise<string>} OSS URL
 */
export function uploadImage(file, dir = 'shops') {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/file/upload?dir=${dir}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export default request
