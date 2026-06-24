// =============================================================
// 主题管理
// 说明：五主题通过给 <body> 加类名 t-gov/t-sand/t-glass/t-emerald/t-editorial
// 实现，选择持久化到 localStorage('grc-theme')，默认 t-gov（朱砂）。
// 与原型「登录页·5主题.html」「驾驶舱版.html」的切换逻辑严格一致。
// =============================================================
import { ref } from 'vue'

const THEME_KEY = 'grc-theme'
// 主题选择器配置：键名、令牌色点（与原型 style="--c:..." 一致）
export const THEME_LIST = [
  { key: 't-gov', color: '#b21d22' },
  { key: 't-sand', color: '#1f6b54' },
  { key: 't-glass', color: '#1f9aa6' },
  { key: 't-emerald', color: '#0c9d6e' },
  { key: 't-editorial', color: '#285c3f' }
]

const THEME_RE = /^t-(gov|sand|glass|emerald|editorial)$/

// 读取持久化主题，非法值回退为 t-gov
function readTheme() {
  const t = localStorage.getItem(THEME_KEY) || 't-gov'
  return THEME_RE.test(t) ? t : 't-gov'
}

// 当前主题（响应式），供各组件高亮选中态
export const currentTheme = ref(readTheme())

// 应用主题：替换 body 类名（清掉所有 t-* 后写入目标主题）
export function applyTheme(theme) {
  const t = THEME_RE.test(theme) ? theme : 't-gov'
  const cls = document.body.className.split(/\s+/).filter((c) => !c.startsWith('t-'))
  cls.push(t)
  document.body.className = cls.join(' ').trim()
  currentTheme.value = t
}

// 切换并持久化主题（供主应用沿用上次选择）
export function setTheme(theme) {
  const t = THEME_RE.test(theme) ? theme : 't-gov'
  applyTheme(t)
  localStorage.setItem(THEME_KEY, t)
}

// 启动初始化：把持久化主题应用到 body
export function initTheme() {
  applyTheme(currentTheme.value)
}
