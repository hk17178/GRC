// =============================================================
// 路由配置（vue-router）
// 说明：
//   /login 登录页、/dashboard 合规态势驾驶舱、/external-audit 外部审计；
//   其余左侧菜单项接「占位路由」（PlaceholderView），保证点击不报错、
//   对应菜单可高亮；待各页原型陆续复原后替换为真实视图组件。
// 每条业务路由带 meta.navKey，供 AppShell 推断当前激活菜单与面包屑。
// 根路径默认重定向到 /login。
// =============================================================
import { createRouter, createWebHashHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import DashboardView from '@/views/DashboardView.vue'
import ExternalAuditView from '@/views/ExternalAuditView.vue'
import RiskAssessmentView from '@/views/RiskAssessmentView.vue'
import RegulatoryAffairsView from '@/views/RegulatoryAffairsView.vue'
import OrgAssetView from '@/views/OrgAssetView.vue'
import BoardView from '@/views/BoardView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'

// 占位菜单项：path → navKey（navKey 既用于菜单高亮，也用于占位页标题与面包屑）
const PLACEHOLDER_PAGES = [
  { path: '/my-tasks', navKey: 'todo' },
  { path: '/internal-audit', navKey: 'audit' },
  { path: '/regulation', navKey: 'law' },
  { path: '/obligations', navKey: 'obligation' },
  { path: '/policy', navKey: 'policy' },
  { path: '/ai-assistant', navKey: 'ai' },
  { path: '/vendor', navKey: 'vendor' },
  { path: '/notify', navKey: 'notify' },
  { path: '/model-access', navKey: 'aimodel' },
  { path: '/permission', navKey: 'perm' },
  { path: '/feedback', navKey: 'feedback' },
  { path: '/settings', navKey: 'settings' }
]

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
    meta: { title: '合规态势', navKey: 'dashboard' }
  },
  {
    path: '/external-audit',
    name: 'external-audit',
    component: ExternalAuditView,
    meta: { title: '外部审计', navKey: 'extaudit' }
  },
  {
    path: '/risk',
    name: 'risk',
    component: RiskAssessmentView,
    meta: { title: '风险评估', navKey: 'risk' }
  },
  {
    path: '/regulatory-affairs',
    name: 'regaffairs',
    component: RegulatoryAffairsView,
    meta: { title: '监管事项', navKey: 'regaffairs' }
  },
  {
    // 组织与资产（M6）：route name 维持 navKey 约定「org」，保证左侧菜单高亮 + 面包屑
    path: '/org',
    name: 'org',
    component: OrgAssetView,
    meta: { title: '组织与资产', navKey: 'org' }
  },
  {
    // 看板与留痕：防篡改操作留痕查询 + 哈希链校验
    path: '/board',
    name: 'board',
    component: BoardView,
    meta: { title: '看板与留痕', navKey: 'board' }
  },
  // 其余菜单项的占位路由（统一用 PlaceholderView，route name 即 navKey）
  ...PLACEHOLDER_PAGES.map((p) => ({
    path: p.path,
    name: p.navKey,
    component: PlaceholderView,
    meta: { navKey: p.navKey }
  })),
  // 兜底：未匹配路由回到登录页
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  // 使用 hash 模式，便于静态托管与原型一致的本地预览
  history: createWebHashHistory(),
  routes
})

export default router
