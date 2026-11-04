import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    // 后端转发：前端 fetch('/api/...') 是同源相对路径，dev 下必须代理到 Spring Boot，
    // 否则会打到 5173 直接 404（VITE_USE_MOCK=false 走真实请求时依赖此代理）。
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
