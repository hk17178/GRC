<template>
  <!--
    第三方供应商（VendorView）：登记 / 评估 / 准入门控启用 / 监测。
    功能真源 = 后端 /api/vendors；视觉遵 tokens.css。准入门控红线：未评估(riskLevel 为空)不得启用，按钮禁用并标因。
  -->
  <AppShell>
    <section class="view view-vendor">
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('vendor.tag') }}</div>
          <h1>{{ $t('vendor.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn" @click="openCreate">{{ $t('vendor.create.btn') }}</button>
      </div>

      <div class="g">
        <!-- 供应商列表 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('vendor.list') }}</h3><span class="cnt">{{ vendors.length }}</span></div>
          <div class="cb" style="overflow-x: auto; padding-top: 0">
            <table style="min-width: 640px">
              <thead><tr>
                <th>{{ $t('vendor.th.code') }}</th><th>{{ $t('vendor.th.name') }}</th>
                <th>{{ $t('vendor.th.risk') }}</th><th>{{ $t('vendor.th.status') }}</th><th>{{ $t('vendor.th.op') }}</th>
              </tr></thead>
              <tbody>
                <tr v-for="v in vendors" :key="v.id" class="clk" :class="{ on: v.id === selectedId }" @click="selectVendor(v)">
                  <td class="code">{{ v.code }}</td>
                  <td>{{ v.name }}</td>
                  <td><span v-if="v.riskLevel" class="tag" :class="riskCls(v.riskLevel)">{{ $t('vendor.level.' + v.riskLevel) }}</span><span v-else class="muted">{{ $t('vendor.unassessed') }}</span></td>
                  <td><span class="st" :class="vStCls(v.status)"><span class="d"></span>{{ $t('vendor.status.' + v.status) }}</span></td>
                  <td class="ops" @click.stop>
                    <button v-if="v.status !== 'TERMINATED'" class="btn ghost sm" @click="openAssess(v)">{{ $t('vendor.op.assess') }}</button>
                    <button v-if="v.status === 'ONBOARDING'" class="btn sm" :disabled="!v.riskLevel || busyId === v.id" :title="!v.riskLevel ? $t('vendor.gateTip') : ''" @click="act(v, 'activate')">{{ $t('vendor.op.activate') }}</button>
                    <button v-else-if="v.status === 'ACTIVE'" class="btn ghost sm" :disabled="busyId === v.id" @click="act(v, 'suspend')">{{ $t('vendor.op.suspend') }}</button>
                    <button v-else-if="v.status === 'SUSPENDED'" class="btn sm" :disabled="busyId === v.id" @click="act(v, 'reactivate')">{{ $t('vendor.op.reactivate') }}</button>
                  </td>
                </tr>
                <tr v-if="!vendors.length"><td colspan="5" class="emptyrow">{{ loadError || $t('vendor.empty') }}</td></tr>
              </tbody>
            </table>
          </div>
          <p v-if="opError" class="cerr" style="padding: 0 18px 12px">{{ opError }}</p>
        </div>

        <!-- 评估历史 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('vendor.assessHist') }}</h3></div>
          <div class="cb" style="overflow-x: auto; padding-top: 0">
            <div v-if="!selectedId" class="hint">{{ $t('vendor.selectHint') }}</div>
            <table v-else style="min-width: 420px">
              <thead><tr><th>{{ $t('vendor.ath.risk') }}</th><th>{{ $t('vendor.ath.score') }}</th><th>{{ $t('vendor.ath.assessor') }}</th><th>{{ $t('vendor.ath.concl') }}</th></tr></thead>
              <tbody>
                <tr v-for="a in assessments" :key="a.id">
                  <td><span class="tag" :class="riskCls(a.riskLevel)">{{ $t('vendor.level.' + a.riskLevel) }}</span></td>
                  <td class="num">{{ a.score == null ? '—' : a.score }}</td>
                  <td>{{ a.assessor }}</td>
                  <td class="desc">{{ a.conclusion }}</td>
                </tr>
                <tr v-if="!assessments.length"><td colspan="4" class="emptyrow">{{ $t('vendor.assessEmpty') }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 登记供应商弹窗 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('vendor.create.btn') }}</h3>
          <label class="fld">{{ $t('vendor.th.code') }}<input v-model="cf.code" /></label>
          <label class="fld">{{ $t('vendor.th.name') }}<input v-model="cf.name" /></label>
          <label class="fld">{{ $t('vendor.create.category') }}<input v-model="cf.category" /></label>
          <label class="fld">{{ $t('vendor.create.criticality') }}<input v-model="cf.criticality" /></label>
          <label class="fld">{{ $t('vendor.create.org') }}
            <select v-model.number="cf.orgId"><option :value="12">{{ $t('vendor.org.pay') }}</option><option :value="13">{{ $t('vendor.org.consumer') }}</option></select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!cf.code || !cf.name || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('vendor.create.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 评估弹窗 -->
      <div v-if="showAssess" class="modal-mask" @click.self="showAssess = false">
        <div class="modal-card">
          <h3>{{ $t('vendor.assess.title') }}</h3>
          <p class="muted" style="margin: -6px 0 14px; font-size: 12.5px">{{ assessTarget && assessTarget.name }}</p>
          <label class="fld">{{ $t('vendor.assess.risk') }}
            <select v-model="af.riskLevel">
              <option value="VERY_LOW">{{ $t('vendor.level.VERY_LOW') }}</option><option value="LOW">{{ $t('vendor.level.LOW') }}</option>
              <option value="MID">{{ $t('vendor.level.MID') }}</option><option value="HIGH">{{ $t('vendor.level.HIGH') }}</option>
              <option value="VERY_HIGH">{{ $t('vendor.level.VERY_HIGH') }}</option>
            </select>
          </label>
          <label class="fld">{{ $t('vendor.assess.score') }}<input v-model.number="af.score" type="number" min="0" max="100" /></label>
          <label class="fld">{{ $t('vendor.assess.concl') }}<input v-model="af.conclusion" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAssess = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="saving" @click="submitAssess">{{ saving ? $t('common.submitting') : $t('vendor.assess.ok') }}</button>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'

const vendors = ref([])
const loadError = ref('')
const selectedId = ref(null)
const assessments = ref([])
const saving = ref(false)
const opError = ref('')
const busyId = ref(null)

async function loadVendors() {
  loadError.value = ''
  try { vendors.value = await api.get('/vendors') } catch (e) { loadError.value = e.message; vendors.value = [] }
}
async function selectVendor(v) {
  selectedId.value = v.id
  try { assessments.value = await api.get('/vendors/' + v.id + '/assessments') } catch (e) { assessments.value = [] }
}
const LEVEL_KEY = { VERY_HIGH: 'vh', HIGH: 'h', MID: 'm', LOW: 'l', VERY_LOW: 'vl' }
const riskCls = (lv) => 'lvl-' + (LEVEL_KEY[lv] || 'm')
const vStCls = (s) => ({ ONBOARDING: 'wait', ACTIVE: 'ok', SUSPENDED: 'over', TERMINATED: 'wait' }[s] || 'wait')

async function act(v, op) {
  busyId.value = v.id; opError.value = ''
  try {
    await api.post('/vendors/' + v.id + '/' + op, {})
    await loadVendors()
  } catch (e) { opError.value = e.message } finally { busyId.value = null }
}

// 登记
const showCreate = ref(false)
const cf = reactive({ code: '', name: '', category: '', criticality: '', orgId: 12 })
function openCreate() { Object.assign(cf, { code: '', name: '', category: '', criticality: '', orgId: 12 }); opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/vendors', { orgId: cf.orgId, code: cf.code, name: cf.name, category: cf.category, criticality: cf.criticality })
    showCreate.value = false; await loadVendors()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 评估
const showAssess = ref(false)
const assessTarget = ref(null)
const af = reactive({ riskLevel: 'MID', score: 70, conclusion: '' })
function openAssess(v) { assessTarget.value = v; Object.assign(af, { riskLevel: 'MID', score: 70, conclusion: '' }); opError.value = ''; showAssess.value = true }
async function submitAssess() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/vendors/' + assessTarget.value.id + '/assessments', { riskLevel: af.riskLevel, score: af.score, conclusion: af.conclusion })
    showAssess.value = false
    await loadVendors()
    if (selectedId.value === assessTarget.value.id) await selectVendor({ id: assessTarget.value.id })
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

onMounted(loadVendors)
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.g { display: grid; grid-template-columns: 1.4fr 1fr; gap: 14px; }
@media (max-width: 980px) { .g { grid-template-columns: 1fr; } }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 4px 10px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.cb { padding: 14px 18px 18px; }
.hint { color: var(--text-3); font-size: 12.5px; padding: 18px; text-align: center; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 14px 10px; }
tbody td { padding: 9px 14px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
tbody tr.clk { cursor: pointer; }
tbody tr.clk:hover, tbody tr.on { background: var(--accent-tint); }
td.ops { display: flex; gap: 6px; }
.num { font-variant-numeric: tabular-nums; }
.code { font-weight: 700; color: var(--accent-strong); }
.desc { color: var(--text-2); max-width: 200px; }
.muted { color: var(--text-3); font-size: 11.5px; }
.tag { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 700; }
.tag.lvl-vh { background: #f1d9d6; color: #7a1620; }
.tag.lvl-h { background: var(--danger-tint); color: var(--danger); }
.tag.lvl-m { background: var(--warning-tint); color: #a87d22; }
.tag.lvl-l { background: var(--safe-weak); color: var(--safe); }
.tag.lvl-vl { background: rgba(120,120,120,0.12); color: var(--text-2); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.over { color: var(--danger); } .st.over .d { background: var(--danger); }
.st.wait .d { background: var(--text-3); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 420px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
</style>
