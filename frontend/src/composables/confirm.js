// =============================================================
// 统一确认对话框（替代浏览器原生 window.confirm）
// 用法：import { confirm } from '@/composables/confirm'
//   if (!await confirm({ title:'确认删除', message:'...', danger:true })) return
// 由全局 <ConfirmDialog/>（挂在 AppShell）渲染，风格与系统 modal 一致。
// =============================================================
import { reactive } from 'vue'

// 单例状态：同一时刻只有一个确认框
export const confirmState = reactive({
  open: false,
  title: '',
  message: '',
  confirmText: '确认',
  cancelText: '取消',
  danger: false,
  _resolve: null
})

/**
 * 弹出统一确认框，返回 Promise<boolean>（确认=true，取消=false）。
 * @param {object|string} opts { title, message, confirmText, cancelText, danger } 或直接传消息字符串
 */
export function confirm(opts) {
  const o = typeof opts === 'string' ? { message: opts } : (opts || {})
  return new Promise((resolve) => {
    confirmState.title = o.title || '请确认'
    confirmState.message = o.message || ''
    confirmState.confirmText = o.confirmText || '确认'
    confirmState.cancelText = o.cancelText || '取消'
    confirmState.danger = !!o.danger
    confirmState._resolve = resolve
    confirmState.open = true
  })
}

/** 由对话框组件调用：结束并回传结果。 */
export function resolveConfirm(result) {
  confirmState.open = false
  const r = confirmState._resolve
  confirmState._resolve = null
  if (r) r(result)
}
