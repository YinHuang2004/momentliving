import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    proxy: {
      // H5 本地联调：/api 走 vite 代理转发到网关（去掉 /api 前缀，网关无 /gateway 前缀）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
        // 源码根目录就是 api/（UNI_INPUT_DIR=.），dev 模式下 /api/xxx.js 等模块请求
        // 会被此前缀误命中，凡带文件扩展名的请求一律不代理，原样交给 Vite 处理。
        // ⚠️ 只看「路径」是否带扩展名，别把 query 也算进去——否则 /api/user/avatar?image=xxx.jpg
        // 会被误判成文件、被 bypass 返回 Vite 得到 404（头像上传就是这么挂的）。
        bypass(req) {
          const pathname = (req.url || '').split('?')[0]
          if (/\.\w+$/.test(pathname)) return req.url
        }
      }
    }
  }
})
