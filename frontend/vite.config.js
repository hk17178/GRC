// =============================================================
// Vite 构建配置
// 说明：标准 Vue 3 工程配置，启用 @vitejs/plugin-vue 单文件组件支持。
// =============================================================
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // 用 @ 指向 src 目录，简化模块引用路径
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173
  }
})
