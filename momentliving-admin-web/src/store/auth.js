import { defineStore } from 'pinia'
import * as adminApi from '../api/admin'

/**
 * 管理员登录态：token/信息持久化到 localStorage
 * token key = 'adminToken'（与用户端/商家端 uniapp 的 userToken/merchantToken 语义隔离）
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('adminToken') || '',
    admin: JSON.parse(localStorage.getItem('adminInfo') || 'null')
  }),
  getters: {
    isLogin: (s) => !!s.token
  },
  actions: {
    async login(username, password) {
      const vo = await adminApi.login({ username, password })
      this.token = vo.token
      this.admin = vo
      localStorage.setItem('adminToken', vo.token)
      localStorage.setItem('adminInfo', JSON.stringify({ ...vo, token: undefined }))
      return vo
    },
    async fetchMe() {
      this.admin = await adminApi.getMe()
      localStorage.setItem('adminInfo', JSON.stringify(this.admin))
      return this.admin
    },
    async logout() {
      try {
        await adminApi.logout()
      } catch (e) {
        // 服务端登出失败也照常清本地
      }
      this.token = ''
      this.admin = null
      localStorage.removeItem('adminToken')
      localStorage.removeItem('adminInfo')
    }
  }
})
