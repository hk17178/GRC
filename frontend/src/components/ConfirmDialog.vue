<!-- =============================================================
     全局确认对话框（统一替代 window.confirm）
     挂在 AppShell，一处渲染，全站通过 confirm() 调用。风格与系统 modal 一致。
     ============================================================= -->
<template>
  <Teleport to="body">
    <div v-if="s.open" class="cfm-mask" @click.self="onCancel">
      <div class="cfm-card" role="alertdialog" aria-modal="true">
        <h3 class="cfm-title">{{ s.title }}</h3>
        <p class="cfm-msg">{{ s.message }}</p>
        <div class="cfm-actions">
          <button class="cfm-btn ghost" @click="onCancel">{{ s.cancelText }}</button>
          <button class="cfm-btn" :class="{ danger: s.danger }" @click="onOk" ref="okBtn">{{ s.confirmText }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { watch, nextTick, ref } from 'vue'
import { confirmState, resolveConfirm } from '@/composables/confirm'

const s = confirmState
const okBtn = ref(null)

function onOk() { resolveConfirm(true) }
function onCancel() { resolveConfirm(false) }

// Esc 取消；打开时焦点落确认键
function onKey(e) {
  if (!s.open) return
  if (e.key === 'Escape') onCancel()
  else if (e.key === 'Enter') onOk()
}
watch(() => s.open, (open) => {
  if (open) {
    window.addEventListener('keydown', onKey)
    nextTick(() => okBtn.value && okBtn.value.focus())
  } else {
    window.removeEventListener('keydown', onKey)
  }
})
</script>

<style scoped>
.cfm-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.34);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}
.cfm-card {
  width: 420px;
  max-width: 92vw;
  background: var(--surface, #fff);
  border: 1px solid var(--surface-border, #e5e7eb);
  border-radius: var(--radius-lg, 14px);
  box-shadow: var(--shadow-2, 0 12px 40px rgba(0, 0, 0, 0.18));
  padding: 22px 24px 18px;
}
.cfm-title {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1, #1a1a1a);
}
.cfm-msg {
  margin: 0 0 18px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--text-2, #444);
  white-space: pre-wrap;
}
.cfm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.cfm-btn {
  padding: 8px 18px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  background: var(--accent, #b03a2e);
  color: #fff;
}
.cfm-btn.danger {
  background: var(--danger, #c0392b);
}
.cfm-btn.ghost {
  background: transparent;
  border-color: var(--surface-border, #d8d8d8);
  color: var(--text-2, #444);
}
.cfm-btn:hover {
  filter: brightness(1.06);
}
</style>
