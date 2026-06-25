// =============================================================
// 应用入口
// 说明：装配 Vue 应用，注册路由与 i18n，全局引入设计令牌与基础样式，
// 并在启动时初始化主题（默认朱砂 t-gov）。
// =============================================================
import { createApp } from 'vue'
import App from './App.vue'
import router from './router/index.js'
import i18n from './i18n.js'
import { initTheme } from './theme.js'
import { setUnauthorizedHandler } from './api/client.js'
import { clearUser } from './auth.js'

// 全局引入设计令牌（五主题 CSS 变量）与基础样式
import './assets/tokens.css'
import './assets/base.css'

// 启动即应用持久化主题到 <body>
initTheme()

// 增强③ R1：会话失效(401)时清登录态并跳登录页（登录/探测接口自身的 401 已在客户端排除）
setUnauthorizedHandler(() => {
  clearUser()
  if (router.currentRoute.value.name !== 'login') {
    router.push({ name: 'login' })
  }
})

createApp(App).use(router).use(i18n).mount('#app')
