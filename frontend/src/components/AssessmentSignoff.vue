<!--
  风险评估·整体等级 + 管理层接受签批（表单引擎 P2 · CR-002 红线）
  ------------------------------------------------------------
  职责：展示由打分服务聚合出的整体残余等级；提供管理层接受签批（意见 + 是否接受残余风险）。
        残余为高/极高且未接受时，后端 complete() 会 409 阻断完成评估——此处给出红线提示与签批入口。
  数据：GET  /api/assessments/{id}            → 含 riskLevel / mgmtSigner / mgmtSignedAt / mgmtOpinion / mgmtAccepted
        POST /api/assessments/{id}/signoff    ← { opinion, accepted }
  说明：写权限由 canWrite('risk') 门控。
-->
<template>
  <div class="signoff card">
    <div class="ch">
      <h3>整体风险 · 管理层签批</h3>
      <span class="sub">残余高/极高须管理层接受方可完成评估（CR-002 红线）</span>
    </div>
    <div class="cb">
      <div v-if="loading" class="muted">加载…</div>
      <template v-else>
        <!-- 整体残余等级 -->
        <div class="row">
          <span class="k">整体残余等级</span>
          <span v-if="detail.riskLevel" class="tag" :class="lvlCls(detail.riskLevel)">{{ lvlLabel(detail.riskLevel) }}</span>
          <span v-else class="muted">— 未评定（先在上方表单填写残余风险并保存）</span>
        </div>

        <!-- 红线提示 -->
        <div v-if="isHigh && !detail.mgmtAccepted" class="redline">
          ⚠ 残余风险为{{ detail.riskLevel === 'VERY_HIGH' ? '极高' : '高' }}，需管理层接受签批后方可完成评估。
        </div>

        <!-- 已签批：展示（含手写签名图与指纹存证） -->
        <div v-if="detail.mgmtSigner" class="signed">
          <div class="srow"><span class="k">签批人</span>{{ detail.mgmtSigner }}<span class="verify">✓ 已重认证身份（密码校验）</span></div>
          <div class="srow"><span class="k">时间</span>{{ fmt(detail.mgmtSignedAt) }}</div>
          <div class="srow"><span class="k">结论</span>
            <span :class="detail.mgmtAccepted ? 'ok' : 'bad'">{{ detail.mgmtAccepted ? '接受残余风险' : '不接受' }}</span>
          </div>
          <div class="srow"><span class="k">意见</span><span class="op">{{ detail.mgmtOpinion || '—' }}</span></div>
          <div v-if="detail.mgmtSignatureSha256" class="srow" style="align-items:flex-start;display:flex">
            <span class="k">手写签名</span>
            <span>
              <img :src="'/api/assessments/' + assessmentId + '/signature?t=' + sigBust" class="sig-img" alt="手写签名" />
              <div class="sig-sha" :title="detail.mgmtSignatureSha256">指纹 sha256：{{ detail.mgmtSignatureSha256.slice(0, 16) }}…（已入防篡改哈希链）</div>
            </span>
          </div>
        </div>

        <!-- 签批表单（V55：手写签名 + 密码重认证） -->
        <div class="signform">
          <label class="fld">管理层意见
            <textarea v-model="form.opinion" rows="2" :disabled="!writable" placeholder="对整体风险与处置的意见"></textarea>
          </label>
          <label class="chk">
            <input type="checkbox" v-model="form.accepted" :disabled="!writable" /> 接受残余风险
          </label>

          <div class="fld">手写签名（框内鼠标签名，或
            <a class="qr-link" @click.prevent="openMobileSign">📱 手机扫码签名</a>）
            <div class="sig-wrap">
              <canvas ref="sigCanvas" class="sig-pad" width="420" height="120"
                      @pointerdown="sigStart" @pointermove="sigMove" @pointerup="sigEnd" @pointerleave="sigEnd"></canvas>
              <button type="button" class="sig-clear" @click="sigClear">清除重签</button>
              <img v-if="mobileSignature" :src="mobileSignature" class="sig-mobile-preview" title="手机签名已取回" />
            </div>
          </div>

          <!-- 手机扫码签名弹层（V57）：二维码 + 轮询取回 -->
          <div v-if="qrVisible" class="qr-mask" @click.self="closeMobileSign">
            <div class="qr-card">
              <h4>手机扫码签名</h4>
              <img v-if="qrDataUrl" :src="qrDataUrl" class="qr-img" alt="签名二维码" />
              <div class="qr-hint">
                用手机（与本机同网络）扫码打开签名页，手写后提交——本页会自动取回。<br/>
                令牌 5 分钟有效、一次性；地址：<span class="qr-url">{{ qrUrl }}</span>
              </div>
              <div class="qr-status" :class="{ ok: mobileSignature }">
                {{ mobileSignature ? '✓ 已取回手机签名，可提交签批' : '等待手机签名中…' }}
              </div>
              <button class="btn ghost" style="margin-top:10px" @click="closeMobileSign">{{ mobileSignature ? '完成' : '取消' }}</button>
            </div>
          </div>
          <label class="fld">登录密码（身份再认证，必填）
            <!-- 安全加固包 A17：重认证密码框严禁浏览器自动填充（否则路过者一键即可冒签） -->
            <input type="password" v-model="form.password" :disabled="!writable" autocomplete="new-password" :readonly="pwdReadonly" @focus="pwdReadonly = false"
                   placeholder="重新输入你的登录密码以确认签批身份" class="pwd" />
          </label>

          <div class="actions">
            <button class="btn" :disabled="!writable || saving || !form.password" @click="submit">
              {{ saving ? '提交中…' : (detail.mgmtSigner ? '重新签批' : '提交签批') }}
            </button>
            <span class="muted" style="font-size:11px">签批 = 密码重认证 + 手写签名存证（sha256 入哈希链）</span>
            <span v-if="okMsg" class="ok">{{ okMsg }}</span>
            <span v-if="error" class="bad">{{ error }}</span>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, watch } from 'vue'
import { api } from '../api/client'
import { canWrite } from '../auth'

const props = defineProps({ assessmentId: { type: [Number, String], default: null } })
const emit = defineEmits(['changed'])

const LVL = {
  VERY_HIGH: { l: '极高', c: 'vh' }, HIGH: { l: '高', c: 'h' }, MID: { l: '中', c: 'm' },
  LOW: { l: '低', c: 'l' }, VERY_LOW: { l: '极低', c: 'vl' }
}
const lvlLabel = (v) => (LVL[v]?.l) || v
const lvlCls = (v) => (LVL[v]?.c) || 'm'

const loading = ref(false)
const error = ref('')
const okMsg = ref('')
const saving = ref(false)
const detail = ref({})
const form = reactive({ opinion: '', accepted: false, password: '' })
const pwdReadonly = ref(true) // A17：readonly-onfocus 技巧阻断浏览器自动填充

// ===== 手写签名板（V55 签批存证）=====
const sigCanvas = ref(null)
const sigBust = ref(0)          // 签名图缓存击穿参数（重新签批后刷新 img）
let sigDrawing = false
let sigDirty = false
function sigCtx() {
  const c = sigCanvas.value
  if (!c) return null
  const ctx = c.getContext('2d')
  ctx.lineWidth = 2.2
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.strokeStyle = getComputedStyle(document.documentElement).getPropertyValue('--text-1') || '#222'
  return ctx
}
function sigPos(e) {
  const r = sigCanvas.value.getBoundingClientRect()
  return { x: e.clientX - r.left, y: e.clientY - r.top }
}
function sigStart(e) {
  if (!writable) return
  sigDrawing = true
  const ctx = sigCtx()
  const p = sigPos(e)
  ctx.beginPath()
  ctx.moveTo(p.x, p.y)
  e.target.setPointerCapture && e.target.setPointerCapture(e.pointerId)
}
function sigMove(e) {
  if (!sigDrawing) return
  const ctx = sigCtx()
  const p = sigPos(e)
  ctx.lineTo(p.x, p.y)
  ctx.stroke()
  sigDirty = true
}
function sigEnd() { sigDrawing = false }
function sigClear() {
  const c = sigCanvas.value
  if (c) c.getContext('2d').clearRect(0, 0, c.width, c.height)
  sigDirty = false
  mobileSignature.value = null
}

// ===== 手机扫码签名（V57）：创建令牌 → 二维码 → 轮询取回 =====
const qrVisible = ref(false)
const qrDataUrl = ref('')
const qrUrl = ref('')
const mobileSignature = ref(null)
let pollTimer = null
let pollToken = null
async function openMobileSign() {
  try {
    const t = await api.post('/assessments/' + props.assessmentId + '/sign-ticket', {})
    pollToken = t.token
    qrUrl.value = window.location.origin + '/#/sign/' + t.token
    const QRCode = (await import('qrcode')).default
    qrDataUrl.value = await QRCode.toDataURL(qrUrl.value, { width: 220, margin: 1 })
    qrVisible.value = true
    pollTimer = setInterval(pollMobile, 2500)
  } catch (e) { error.value = e.message }
}
async function pollMobile() {
  try {
    const r = await api.get('/assessments/' + props.assessmentId + '/sign-ticket/' + pollToken)
    if (r.status === 'SIGNED' && r.signatureDataUrl) {
      mobileSignature.value = r.signatureDataUrl
      clearInterval(pollTimer); pollTimer = null
    } else if (r.status === 'EXPIRED' || r.status === 'USED') {
      clearInterval(pollTimer); pollTimer = null
    }
  } catch (e) { /* 轮询容错 */ }
}
function closeMobileSign() {
  qrVisible.value = false
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

const writable = canWrite('risk')
const isHigh = computed(() => detail.value.riskLevel === 'HIGH' || detail.value.riskLevel === 'VERY_HIGH')

function fmt(t) {
  if (!t) return '—'
  try { return new Date(t).toLocaleString() } catch { return t }
}

async function load() {
  if (!props.assessmentId) return
  loading.value = true; error.value = ''
  try {
    detail.value = await api.get('/assessments/' + props.assessmentId)
    form.opinion = detail.value.mgmtOpinion || ''
    form.accepted = !!detail.value.mgmtAccepted
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function submit() {
  saving.value = true; okMsg.value = ''; error.value = ''
  try {
    // 手机取回的签名优先；否则用本地手写板
    const signatureDataUrl = mobileSignature.value
        || (sigDirty && sigCanvas.value ? sigCanvas.value.toDataURL('image/png') : null)
    await api.post('/assessments/' + props.assessmentId + '/signoff', {
      opinion: form.opinion, accepted: form.accepted,
      password: form.password, signatureDataUrl
    })
    okMsg.value = '已签批（身份已重认证）'
    form.password = ''
    sigClear()
    sigBust.value = Date.now()
    await load()
    emit('changed')
    setTimeout(() => (okMsg.value = ''), 2500)
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

// 供父组件在保存表单后刷新整体等级
defineExpose({ reload: load })

watch(() => props.assessmentId, load, { immediate: true })
</script>

<style scoped>
.signoff { margin-bottom: 16px; }
.signoff .sub { color: var(--text-3); font-size: 12px; }
.muted { color: var(--text-3); }
.row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.row .k, .srow .k { color: var(--text-2); font-size: 12px; display: inline-block; min-width: 84px; }
.redline {
  background: color-mix(in srgb, var(--danger) 12%, transparent);
  border: 1px solid var(--danger); color: var(--danger);
  padding: 8px 10px; border-radius: 6px; font-size: 13px; margin-bottom: 12px;
}
.signed { background: var(--surface-2); border-radius: 8px; padding: 10px 12px; margin-bottom: 12px; }
.srow { margin-bottom: 4px; font-size: 13px; }
.srow .op { white-space: pre-wrap; }
.signform .fld { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-2); margin-bottom: 8px; }
.signform textarea {
  width: 100%; padding: 6px 8px; border: 1px solid var(--border); border-radius: 6px;
  background: var(--surface); color: var(--text-1); font: inherit; box-sizing: border-box;
}
.chk { display: flex; align-items: center; gap: 6px; font-size: 13px; margin-bottom: 10px; }
.actions { display: flex; align-items: center; gap: 10px; }
.ok { color: var(--success); font-weight: 600; }
.bad { color: var(--danger); }
/* V55 签批存证 */
.verify { margin-left: 10px; font-size: 11px; color: var(--success); font-weight: 600; }
.sig-wrap { position: relative; display: inline-block; }
.sig-pad { display: block; border: 1px dashed var(--surface-border); border-radius: 8px; background: var(--surface); cursor: crosshair; touch-action: none; }
.sig-clear { position: absolute; right: 6px; top: 6px; font-size: 11px; padding: 2px 8px; border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-3); border-radius: 6px; cursor: pointer; }
.sig-clear:hover { color: var(--danger); border-color: var(--danger); }
.pwd { width: 320px; max-width: 100%; height: 34px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: 6px; background: var(--surface); color: var(--text-1); font: inherit; box-sizing: border-box; }
.sig-img { max-height: 72px; background: #fff; border: 1px solid var(--border-subtle); border-radius: 6px; padding: 2px 6px; }
.sig-sha { font-size: 10.5px; color: var(--text-3); margin-top: 3px; }
/* V57 手机扫码签名 */
.qr-link { color: var(--accent-strong); cursor: pointer; font-weight: 600; text-decoration: underline; }
.sig-mobile-preview { position: absolute; left: 8px; top: 8px; max-height: 100px; max-width: 400px; pointer-events: none; }
.qr-mask { position: fixed; inset: 0; background: rgba(0,0,0,.35); display: flex; align-items: center; justify-content: center; z-index: 60; }
.qr-card { width: 320px; background: var(--surface); border: 1px solid var(--surface-border); border-radius: 12px; box-shadow: var(--shadow-2, 0 10px 40px rgba(0,0,0,.2)); padding: 20px 22px; text-align: center; }
.qr-card h4 { margin: 0 0 12px; font-size: 15px; }
.qr-img { width: 200px; height: 200px; background: #fff; border-radius: 8px; }
.qr-hint { font-size: 11px; color: var(--text-3); line-height: 1.7; margin-top: 8px; text-align: left; }
.qr-url { word-break: break-all; color: var(--text-2); }
.qr-status { margin-top: 10px; font-size: 12.5px; font-weight: 600; color: #a87d22; }
.qr-status.ok { color: var(--success); }
</style>
