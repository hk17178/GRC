<template>
  <!--
    手机手写签名页（V57 · 免登录轻页面）：桌面签批弹二维码 → 手机扫码到此 →
    凭一次性令牌（5 分钟）手写签名提交 → 桌面自动取回。不加载 AppShell、不触达业务数据。
  -->
  <div class="signpad-page">
    <div class="sp-card">
      <div class="sp-brand">Mandao GRC · {{ $t('signpad.brand') }}</div>
      <template v-if="state === 'ready'">
        <div class="sp-title">{{ info.title || $t('signpad.defaultTitle') }}</div>
        <div class="sp-hint">{{ $t('signpad.hint') }}</div>
        <canvas ref="pad" class="sp-pad"
                @pointerdown="start" @pointermove="move" @pointerup="end" @pointerleave="end"></canvas>
        <div class="sp-actions">
          <button class="sp-btn ghost" @click="clearPad">{{ $t('signpad.clear') }}</button>
          <button class="sp-btn" :disabled="!dirty || busy" @click="submit">{{ busy ? $t('common.submitting') : $t('signpad.submit') }}</button>
        </div>
        <div class="sp-exp">{{ $t('signpad.expLabel', { t: expText }) }}</div>
      </template>
      <template v-else-if="state === 'done'">
        <div class="sp-done">{{ $t('signpad.doneMsg') }}</div>
        <div class="sp-hint">{{ $t('signpad.doneHint') }}</div>
      </template>
      <template v-else>
        <div class="sp-err">{{ errText }}</div>
        <div class="sp-hint">{{ $t('signpad.invalidHint') }}</div>
      </template>
      <p v-if="error" class="sp-err" style="margin-top:8px">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api } from '@/api/client.js'

const { t } = useI18n()
const route = useRoute()
const token = route.params.token
const state = ref('loading')     // loading / ready / done / invalid
const info = ref({})
const errText = ref('')
const error = ref('')
const busy = ref(false)
const dirty = ref(false)
const pad = ref(null)
const expText = ref('')

let drawing = false
function ctx2d() {
  const c = pad.value
  const ctx = c.getContext('2d')
  ctx.lineWidth = 3
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.strokeStyle = '#1c1c1c'
  return ctx
}
function pos(e) {
  const r = pad.value.getBoundingClientRect()
  // canvas 内部坐标按实际分辨率换算（触屏 DPR 场景）
  return { x: (e.clientX - r.left) * (pad.value.width / r.width), y: (e.clientY - r.top) * (pad.value.height / r.height) }
}
function start(e) { drawing = true; const c = ctx2d(); const p = pos(e); c.beginPath(); c.moveTo(p.x, p.y); e.target.setPointerCapture && e.target.setPointerCapture(e.pointerId) }
function move(e) { if (!drawing) return; const c = ctx2d(); const p = pos(e); c.lineTo(p.x, p.y); c.stroke(); dirty.value = true }
function end() { drawing = false }
function clearPad() { const c = pad.value; c.getContext('2d').clearRect(0, 0, c.width, c.height); dirty.value = false }

async function submit() {
  busy.value = true; error.value = ''
  try {
    await api.post('/sign/' + token, { signatureDataUrl: pad.value.toDataURL('image/png') })
    state.value = 'done'
  } catch (e) { error.value = e.message } finally { busy.value = false }
}

onMounted(async () => {
  try {
    const t = await api.get('/sign/' + token)
    info.value = t
    if (t.status === 'PENDING') {
      state.value = 'ready'
      expText.value = new Date(t.expiresAt).toLocaleTimeString()
      // 画布分辨率适配容器宽度
      requestAnimationFrame(() => {
        if (pad.value) {
          const w = pad.value.parentElement.clientWidth - 4
          pad.value.width = Math.min(680, Math.max(320, w))
          pad.value.height = 200
        }
      })
    } else if (t.status === 'SIGNED' || t.status === 'USED') {
      state.value = 'done'
    } else {
      state.value = 'invalid'
      errText.value = t('signpad.errExpired')
    }
  } catch (e) {
    state.value = 'invalid'
    errText.value = t('signpad.errInvalid')
  }
})
</script>

<style scoped>
.signpad-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--bg, #f4f1ec); padding: 16px; }
.sp-card { width: 100%; max-width: 720px; background: var(--surface, #fff); border: 1px solid var(--surface-border, #e5ded2); border-radius: 14px; box-shadow: 0 8px 32px rgba(0,0,0,.08); padding: 22px 20px; }
.sp-brand { font-size: 11px; letter-spacing: 1.5px; color: var(--accent, #a02c2c); font-weight: 700; text-transform: uppercase; margin-bottom: 10px; }
.sp-title { font-size: 17px; font-weight: 720; color: var(--text-1, #222); margin-bottom: 6px; }
.sp-hint { font-size: 12.5px; color: var(--text-3, #999); margin-bottom: 12px; line-height: 1.6; }
.sp-pad { display: block; width: 100%; border: 1.5px dashed var(--surface-border, #d8cfc0); border-radius: 10px; background: #fff; touch-action: none; cursor: crosshair; }
.sp-actions { display: flex; gap: 10px; margin-top: 12px; }
.sp-btn { flex: 1; height: 42px; border: 0; border-radius: 10px; background: var(--accent, #a02c2c); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; }
.sp-btn.ghost { background: var(--bg, #f4f1ec); color: var(--text-2, #555); border: 1px solid var(--surface-border, #ddd); }
.sp-btn[disabled] { opacity: .5; }
.sp-exp { margin-top: 10px; font-size: 11px; color: var(--text-3, #999); text-align: center; }
.sp-done { font-size: 20px; font-weight: 720; color: var(--success, #2e8b57); margin: 12px 0 6px; }
.sp-err { font-size: 13px; color: var(--danger, #c0392b); }
</style>
