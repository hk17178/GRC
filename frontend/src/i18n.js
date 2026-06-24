// =============================================================
// vue-i18n 实例
// 说明：中英双语，语言选择持久化到 localStorage('grc-lang')，默认 zh。
// 组件内通过 $t('xxx') 取文案；HTML 文案（含 <br>）用 v-html 渲染。
// =============================================================
import { createI18n } from 'vue-i18n'
import zh from './locales/zh.js'
import en from './locales/en.js'

// 读取持久化语言，非法值回退为 zh
const LANG_KEY = 'grc-lang'
let lang = localStorage.getItem(LANG_KEY) || 'zh'
if (lang !== 'zh' && lang !== 'en') lang = 'zh'

const i18n = createI18n({
  legacy: false, // 使用 Composition API 模式
  globalInjection: true, // 允许模板里直接用 $t
  locale: lang,
  fallbackLocale: 'zh',
  messages: { zh, en }
})

// 切换语言并持久化；同时同步 <html lang> 属性
export function setLang(next) {
  const v = next === 'en' ? 'en' : 'zh'
  i18n.global.locale.value = v
  localStorage.setItem(LANG_KEY, v)
  document.documentElement.lang = v === 'en' ? 'en' : 'zh-CN'
}

// 启动时同步一次 <html lang>
document.documentElement.lang = lang === 'en' ? 'en' : 'zh-CN'

export default i18n
