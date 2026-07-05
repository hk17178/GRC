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
import PermissionView from '@/views/PermissionView.vue'
import AiAssistantView from '@/views/AiAssistantView.vue'
import ModelAccessView from '@/views/ModelAccessView.vue'
import ApprovalFlowDesignerView from '@/views/ApprovalFlowDesignerView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import InternalAuditView from '@/views/InternalAuditView.vue'

// 占位菜单项：path → navKey（navKey 既用于菜单高亮，也用于占位页标题与面包屑）
// ⑦ 内部审计已实建（见下方路由），不再占位。
const PLACEHOLDER_PAGES = []

const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { title: '登录' }
  },
  {
    // V57 手机手写签名页：免登录轻页面（token 即凭证，5 分钟一次性），不走 AppShell
    path: '/sign/:token',
    name: 'signpad',
    component: () => import('@/views/SignPadView.vue'),
    meta: { title: '手写签名' }
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
    // ⑦ 内部审计（M3）：审计计划/发现/整改，复用审计管理后端按 type=INTERNAL
    path: '/internal-audit',
    name: 'audit',
    component: InternalAuditView,
    meta: { title: '内部审计', navKey: 'audit' }
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
    // 权限与职责分离：用户角色授权 + SoD 红线 + 例外审批（M8）
    path: '/permission',
    name: 'perm',
    component: PermissionView,
    meta: { title: '权限与职责分离', navKey: 'perm' }
  },
  {
    // AI 智能问答：基于知识库的检索增强问答（CR-004 RAG）
    path: '/ai-assistant',
    name: 'ai',
    component: AiAssistantView,
    meta: { title: 'AI 智能问答', navKey: 'ai' }
  },
  {
    // 模型接入：AI 提供方/嵌入配置状态 + 切换指引
    path: '/model-access',
    name: 'aimodel',
    component: ModelAccessView,
    meta: { title: '模型接入', navKey: 'aimodel' }
  },
  {
    // 审批流配置：Vue Flow 可视化画布（增强② P1.4）
    path: '/approval-flows',
    name: 'approvalflow',
    component: ApprovalFlowDesignerView,
    meta: { title: '审批流配置', navKey: 'approvalflow' }
  },
  {
    // ⑧ 权限配置已并入「权限管理」(/permission 的「角色权限矩阵」Tab)，旧路径重定向
    path: '/rbac-config',
    redirect: '/permission'
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
  // 安全加固包 B17：首登强制改密页
  {
    path: '/change-password',
    name: 'change-password',
    component: () => import('@/views/ChangePasswordView.vue'),
    meta: { title: '修改口令' }
  },
  // 安全加固包 A33：兜底改指向仪表盘（已登录时误触路由不再被踢回登录页；未登录会被守卫拦到登录）
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  // 使用 hash 模式，便于静态托管与原型一致的本地预览
  history: createWebHashHistory(),
  routes
})

// ---- 认证守卫（增强③ R1）----
// 除登录页外，所有路由要求已登录；首次进入时探测会话(/auth/me)，未登录跳登录页。
import { authState, refreshAuth, canSee } from '@/auth.js'
router.beforeEach(async (to) => {
  if (to.name === 'login' || to.name === 'signpad') return true // signpad：手机免登录签名页（token 即凭证）
  if (!authState.ready) await refreshAuth()
  if (!authState.user) return { name: 'login' }
  // B17：首登强制改密——未改密前只能停在改密页
  if (authState.user.mustChangePassword && to.name !== 'change-password') {
    return { name: 'change-password' }
  }
  // 菜单可见性门控（增强③ R4）：访问隐藏菜单 → 回态势页
  const navKey = to.meta?.navKey
  if (navKey && Object.keys(authState.perms).length > 0 && !canSee(navKey) && navKey !== 'dashboard') {
    return { name: 'dashboard' }
  }
  return true
})

export default router
