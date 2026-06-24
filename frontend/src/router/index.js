// =============================================================
// 路由配置（vue-router）
// 说明：/login 登录页、/dashboard 仪表盘占位页（用 AppShell 外壳包裹）。
// 根路径默认重定向到 /login。
// =============================================================
import { createRouter, createWebHashHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import DashboardView from '@/views/DashboardView.vue'

const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { title: '登录' }
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: DashboardView,
    meta: { title: '合规态势' }
  },
  // 兜底：未匹配路由回到登录页
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  // 使用 hash 模式，便于静态托管与原型一致的本地预览
  history: createWebHashHistory(),
  routes
})

export default router
