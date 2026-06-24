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
import MyTasksView from '@/views/MyTasksView.vue'
import NotifyView from '@/views/NotifyView.vue'
import RegulationView from '@/views/RegulationView.vue'
import VendorView from '@/views/VendorView.vue'
import ObligationView from '@/views/ObligationView.vue'
import FeedbackView from '@/views/FeedbackView.vue'
import SettingsView from '@/views/SettingsView.vue'
import PolicyView from '@/views/PolicyView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'

// 占位菜单项：path → navKey（navKey 既用于菜单高亮，也用于占位页标题与面包屑）
const PLACEHOLDER_PAGES = [
  { path: '/internal-audit', navKey: 'audit' },
  { path: '/ai-assistant', navKey: 'ai' },
  { path: '/model-access', navKey: 'aimodel' },
  { path: '/permission', navKey: 'perm' }
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
  {
    // 我的待办：跨模块归并待处理工作
    path: '/my-tasks',
    name: 'todo',
    component: MyTasksView,
    meta: { title: '我的待办', navKey: 'todo' }
  },
  {
    // 通知中心：调度内核派发的提醒
    path: '/notify',
    name: 'notify',
    component: NotifyView,
    meta: { title: '通知中心', navKey: 'notify' }
  },
  {
    // 法规跟踪：法规库 + 变更动态 + 影响评估闭环
    path: '/regulation',
    name: 'law',
    component: RegulationView,
    meta: { title: '法规跟踪', navKey: 'law' }
  },
  {
    // 第三方供应商：准入/评估/监测（准入门控红线）
    path: '/vendor',
    name: 'vendor',
    component: VendorView,
    meta: { title: '第三方供应商', navKey: 'vendor' }
  },
  {
    // 合规清单：合规义务库 + 落实追踪（落实证据红线）
    path: '/obligations',
    name: 'obligation',
    component: ObligationView,
    meta: { title: '合规清单', navKey: 'obligation' }
  },
  {
    // 制度发布：制度全生命周期 + 审批两步化（M1）
    path: '/policy',
    name: 'policy',
    component: PolicyView,
    meta: { title: '制度发布', navKey: 'policy' }
  },
  {
    // 建议与反馈：反馈生命周期（办结须留处置结果）
    path: '/feedback',
    name: 'feedback',
    component: FeedbackView,
    meta: { title: '建议与反馈', navKey: 'feedback' }
  },
  {
    // 系统设置：租户键值配置（锁定项不可改）
    path: '/settings',
    name: 'settings',
    component: SettingsView,
    meta: { title: '系统设置', navKey: 'settings' }
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
