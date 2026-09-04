import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 管理后台 Web（独立工程，不与 uniapp 混用构建链）
// 开发期 /api 走 vite 代理直连网关 8080，生产由 nginx 反代到网关
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 网关路由按真实路径分发：/admin/** → admin-service，/shop/** → shop-service，其余同理
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
