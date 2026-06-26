<!-- =============================================================
     已登录区外壳（AppShell）
     说明：复原驾驶舱版原型的「左侧导航 + 顶栏」骨架：
       · 左侧侧栏：品牌 + 分组菜单（信息架构对齐原型 nav）；
       · 顶栏：面包屑 + 语言切换 + 主题切换 + 搜索 + 通知 + 头像。
     配色/形态复用 tokens.css，组件内只引用语义令牌。
     菜单文案走 i18n；徽标数(cnt)为原型示例数据。
     ============================================================= -->
<template>
  <div class="app">
    <!-- ===== 左侧导航 ===== -->
    <aside class="side">
      <div class="brand">
        <div class="logo">G</div>
        <div>
          <b>{{ $t('common.brandName') }}</b>
          <span>{{ $t('common.brandSub') }}</span>
        </div>
      </div>
      <nav class="nav">
        <template v-for="grp in visibleMenu" :key="grp.group">
          <div class="grp">{{ $t('navGroup.' + grp.group) }}</div>
          <a
            v-for="it in grp.items"
            :key="it.key"
            :class="{ on: it.key === activeKey }"
            @click="onNav(it.key)"
          >
            <!-- 图标用统一占位（保持原型菜单项左侧图标位） -->
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
              <rect x="3" y="3" width="7" height="7" rx="1.5" />
              <rect x="14" y="3" width="7" height="7" rx="1.5" />
              <rect x="14" y="14" width="7" height="7" rx="1.5" />
              <rect x="3" y="14" width="7" height="7" rx="1.5" />
            </svg>
            {{ $t('nav.' + it.key) }}
            <span v-if="it.cnt" class="cnt num">{{ it.cnt }}</span>
          </a>
        </template>
      </nav>
    </aside>

    <!-- ===== 主区 ===== -->
    <div class="main">
      <header class="top">
        <div class="crumb">
          {{ $t('crumb.' + activeKey + '.g') }} / <b>{{ $t('crumb.' + activeKey + '.c') }}</b>
        </div>
        <div class="right">
          <LangSwitch />
          <ThemeSwitch />
          <div class="searchwrap">
            <div class="search">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="7" />
                <path d="m21 21-4-4" />
              </svg>
              <input :placeholder="$t('top.searchPlaceholder')" />
            </div>
          </div>
          <div class="ico">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
            </svg>
            <span class="dot"></span>
          </div>
          <div class="userbox">
            <div class="av" :title="displayName">{{ avatarChar }}</div>
            <span class="uname">{{ displayName }}</span>
            <button class="logout" :title="$t('top.logout')" @click="logout">{{ $t('top.logout') }}</button>
          </div>
        </div>
      </header>

      <!-- 业务内容插槽 -->
      <div class="content">
        <slot />
      </div>
    </div>

    <!-- ===== 全局浮动「AI 助手」按钮（左下角，所有页可见）=====
         复原驾驶舱版原型 <button class="fab" id="aiFab">：渐变方圆角 +
         右上角金色 AI 角标 + 对话气泡图标。当前点击仅占位（console），不接后端。 -->
    <button class="fab" :title="$t('aiFab.title')" @click="onAiFab">
      <span class="badge">AI</span>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      </svg>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LangSwitch from '@/components/LangSwitch.vue'
import ThemeSwitch from '@/components/ThemeSwitch.vue'
import { api } from '@/api/client.js'
import { authState, canSee, clearUser } from '@/auth.js'

const route = useRoute()
const router = useRouter()

// 当前登录人显示名 + 头像首字（增强③ R4）
const displayName = computed(() => authState.user?.displayName || authState.user?.username || '')
const avatarChar = computed(() => (displayName.value || '·').charAt(0))

/** 登出：清后端 Cookie + 前端态 → 回登录页。 */
async function logout() {
  try { await api.post('/auth/logout', {}) } catch (e) { /* 忽略 */ }
  clearUser()
  router.push({ name: 'login' })
}

// 菜单键 → 路由名映射。
// 约定：占位路由的 route name 直接取 navKey；仅驾驶舱/外部审计两条命名不同，单独列出。
// 与 router/index.js 的 PLACEHOLDER_PAGES（name=navKey）保持一致。
const ROUTE_BY_KEY = {
  dashboard: 'dashboard',
  extaudit: 'external-audit',
  todo: 'todo',
  audit: 'audit',
  risk: 'risk',
  law: 'law',
  regaffairs: 'regaffairs',
  obligation: 'obligation',
  policy: 'policy',
  ai: 'ai',
  vendor: 'vendor',
  org: 'org',
  notify: 'notify',
  aimodel: 'aimodel',
  perm: 'perm',
  board: 'board',
  feedback: 'feedback',
  settings: 'settings',
  approvalflow: 'approvalflow'
}

// 当前激活菜单：优先按当前路由的 meta.navKey 推断，回退按 route name 反查，再回退 dashboard
const activeKey = computed(() => {
  if (route.meta?.navKey) return route.meta.navKey
  const hit = Object.keys(ROUTE_BY_KEY).find((k) => ROUTE_BY_KEY[k] === route.name)
  return hit || 'dashboard'
})

// 菜单信息架构（对齐驾驶舱版原型 nav 分组与顺序）
const menu = [
  {
    group: 'overview',
    items: [
      { key: 'dashboard' },
      { key: 'todo', cnt: 8 }
    ]
  },
  {
    group: 'business',
    items: [
      { key: 'extaudit' },
      { key: 'audit', cnt: 23 },
      { key: 'risk', cnt: 248 },
      { key: 'law' },
      { key: 'regaffairs', cnt: 5 },
      { key: 'obligation', cnt: 13 },
      { key: 'policy', cnt: 312 },
      { key: 'ai' },
      { key: 'vendor' }
    ]
  },
  {
    group: 'asset',
    items: [{ key: 'org' }]
  },
  {
    group: 'system',
    items: [
      { key: 'notify' },
      { key: 'aimodel' },
      { key: 'perm' },
      { key: 'approvalflow' },
      { key: 'board' },
      { key: 'feedback', cnt: 6 },
      { key: 'settings' }
    ]
  }
]

// 按权限过滤菜单（增强③ R4）：仅显示对该菜单非 HIDDEN（RW/RO）的项；空分组隐藏。
// 权限未就绪时(authState.perms 为空)显示全部，避免登录瞬时空菜单闪烁。
const visibleMenu = computed(() => {
  const hasPerms = Object.keys(authState.perms).length > 0
  if (!hasPerms) return menu
  return menu
    .map((grp) => ({ group: grp.group, items: grp.items.filter((it) => canSee(it.key)) }))
    .filter((grp) => grp.items.length > 0)
})

// 菜单点击：跳转对应路由（外部审计/驾驶舱为真实页，其余为占位页）
function onNav(key) {
  const name = ROUTE_BY_KEY[key]
  if (name && route.name !== name) {
    router.push({ name })
  }
}

// 浮动 AI 助手按钮点击：当前仅占位（不接后端），打印日志便于联调
function onAiFab() {
  // eslint-disable-next-line no-console
  console.log('[AI 助手] 浮动按钮点击（占位，后续接入 AI 合规助手面板）')
}
</script>

<style scoped>
/* 骨架样式严格对齐驾驶舱版原型 .app/.side/.nav/.top 等 */
.app {
  display: flex;
  min-height: 100vh;
  /* 显式视口宽度：与 LoginView .login-root 同理——否则该 flex 根容器在整页加载时
     会收缩到「侧栏宽度 + 主区 0」导致主区塌缩成窄条（默认主题下尤为明显）。 */
  width: 100vw;
}
.side {
  width: 226px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 0 12px;
  background: var(--side-bg);
  border-right: 1px solid var(--border);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}
.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px;
  flex-shrink: 0;
}
.brand .logo {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-md);
  background: linear-gradient(145deg, var(--accent-bright), var(--accent-strong));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 15px;
}
.brand b {
  font-size: 14px;
  font-weight: 720;
  color: var(--text-1);
}
.brand span {
  font-size: 9.5px;
  color: var(--text-3);
  display: block;
  margin-top: 1px;
}
.nav .grp {
  font-size: 10px;
  color: var(--text-4);
  padding: 13px 11px 5px;
  font-weight: 700;
  letter-spacing: 1px;
  text-transform: uppercase;
}
.nav a {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 11px;
  color: var(--side-fg);
  text-decoration: none;
  font-size: 12.5px;
  border-radius: var(--radius-md);
  margin-bottom: 1px;
  cursor: pointer;
  position: relative;
}
.nav a svg {
  width: 16px;
  height: 16px;
  color: var(--text-3);
}
.nav a:hover {
  background: var(--accent-tint);
  color: var(--accent-strong);
}
.nav a:hover svg {
  color: var(--accent);
}
.nav a.on {
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: #fff;
  font-weight: 600;
}
.nav a.on svg {
  color: #fff;
}
.nav a .cnt {
  margin-left: auto;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--text-3);
  background: rgba(120, 120, 120, 0.12);
  padding: 0 7px;
  border-radius: 9px;
}
.nav a.on .cnt {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}

/* —— 朱砂 t-gov 侧栏配色覆盖（白字红底 + 金色选中条）—— */
:global(body.t-gov) .brand .logo {
  background: linear-gradient(145deg, #f0d690, var(--accent-gold));
  color: var(--accent-strong);
}
:global(body.t-gov) .brand b {
  color: #fff;
}
:global(body.t-gov) .brand span {
  color: #e7b9bb;
}
:global(body.t-gov) .nav .grp {
  color: #e09fa1;
}
:global(body.t-gov) .nav a,
:global(body.t-gov) .nav a svg {
  color: #f0cdce;
}
:global(body.t-gov) .nav a:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}
:global(body.t-gov) .nav a:hover svg {
  color: #fff;
}
:global(body.t-gov) .nav a.on {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}
:global(body.t-gov) .nav a.on svg {
  color: #fff;
}
:global(body.t-gov) .nav a.on::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 7px;
  bottom: 7px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--accent-gold);
}
:global(body.t-gov) .nav a .cnt {
  background: rgba(0, 0, 0, 0.16);
  color: #f0cdce;
}

/* —— 晴空/苍翠 深色渐变侧栏：白字 —— */
:global(body.t-glass) .nav a,
:global(body.t-emerald) .nav a {
  color: var(--side-fg);
}
:global(body.t-glass) .brand b,
:global(body.t-emerald) .brand b {
  color: #fff;
}
:global(body.t-glass) .nav a:hover,
:global(body.t-emerald) .nav a:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}
:global(body.t-glass) .nav a:hover svg,
:global(body.t-emerald) .nav a:hover svg {
  color: #fff;
}
:global(body.t-glass) .nav a.on,
:global(body.t-emerald) .nav a.on {
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
}
:global(body.t-glass) .nav a svg,
:global(body.t-emerald) .nav a svg {
  color: var(--side-fg);
}
:global(body.t-glass) .nav .grp,
:global(body.t-emerald) .nav .grp {
  color: rgba(255, 255, 255, 0.5);
}

/* ===== 主区 / 顶栏 ===== */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.top {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  gap: 14px;
  position: sticky;
  top: 0;
  z-index: 20;
  background: var(--topbar-bg);
  border-bottom: 1px solid var(--border);
}
:global(body.t-gov) .top {
  border-bottom: 2px solid var(--accent);
}
.crumb {
  font-size: 12.5px;
  color: var(--text-3);
}
.crumb b {
  color: var(--text-1);
  font-weight: 680;
}
.top .right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.search {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 40px;
  padding: 0 12px;
  border-radius: var(--radius-md);
  border: 1px solid var(--surface-border);
  background: var(--surface);
  color: var(--text-3);
  box-shadow: var(--shadow-1);
}
.search input {
  border: 0;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--text-1);
  width: 180px;
}
.ico {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--surface);
  border: 1px solid var(--surface-border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  position: relative;
  box-shadow: var(--shadow-1);
}
.ico .dot {
  position: absolute;
  top: 8px;
  right: 9px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--danger);
}
.av {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: linear-gradient(145deg, var(--accent-bright), var(--accent-strong));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
}
.userbox {
  display: flex;
  align-items: center;
  gap: 9px;
}
.userbox .uname {
  font-size: 12.5px;
  color: var(--text-2);
  font-weight: 600;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.userbox .logout {
  border: 1px solid var(--surface-border);
  background: var(--surface);
  color: var(--text-2);
  font-size: 11.5px;
  padding: 5px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-family: inherit;
}
.userbox .logout:hover {
  color: var(--danger);
  border-color: var(--danger);
}
.content {
  padding: 22px 24px 40px;
  flex: 1;
  overflow-y: auto;
}

/* ===== 全局浮动「AI 助手」按钮 fab（严格对齐原型 .fab）===== */
.fab {
  position: fixed;
  bottom: 18px;
  left: 18px;
  z-index: 62;
  width: 50px;
  height: 50px;
  border-radius: 15px;
  border: 0;
  cursor: pointer;
  background: linear-gradient(145deg, var(--accent-bright), var(--accent-strong));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-2);
}
.fab svg {
  width: 21px;
  height: 21px;
}
.fab .badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--accent-gold);
  border: 2px solid #fff;
  font-size: 8.5px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
}
</style>
