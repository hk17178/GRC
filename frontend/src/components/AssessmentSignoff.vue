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

        <!-- 已签批：展示 -->
        <div v-if="detail.mgmtSigner" class="signed">
          <div class="srow"><span class="k">签批人</span>{{ detail.mgmtSigner }}</div>
          <div class="srow"><span class="k">时间</span>{{ fmt(detail.mgmtSignedAt) }}</div>
          <div class="srow"><span class="k">结论</span>
            <span :class="detail.mgmtAccepted ? 'ok' : 'bad'">{{ detail.mgmtAccepted ? '接受残余风险' : '不接受' }}</span>
          </div>
          <div class="srow"><span class="k">意见</span><span class="op">{{ detail.mgmtOpinion || '—' }}</span></div>
        </div>

        <!-- 签批表单 -->
        <div class="signform">
          <label class="fld">管理层意见
            <textarea v-model="form.opinion" rows="2" :disabled="!writable" placeholder="对整体风险与处置的意见"></textarea>
          </label>
          <label class="chk">
            <input type="checkbox" v-model="form.accepted" :disabled="!writable" /> 接受残余风险
          </label>
          <div class="actions">
            <button class="btn" :disabled="!writable || saving" @click="submit">
              {{ saving ? '提交中…' : (detail.mgmtSigner ? '重新签批' : '提交签批') }}
            </button>
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
const form = reactive({ opinion: '', accepted: false })

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
    await api.post('/assessments/' + props.assessmentId + '/signoff', { opinion: form.opinion, accepted: form.accepted })
    okMsg.value = '已签批'
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
</style>
