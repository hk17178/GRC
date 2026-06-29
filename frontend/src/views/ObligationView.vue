<template>
  <!--
    合规清单（ObligationView）：合规义务库 + 落实追踪。
    功能真源 = 后端 /api/obligations；视觉遵 tokens.css。落实闭环红线：标记已落实须留证据（落实弹窗证据必填）。
  -->
  <AppShell>
    <section class="view view-obl">
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('obl.tag') }}</div>
          <h1>{{ $t('obl.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn" :disabled="!canWrite('obligation')" :title="canWrite('obligation') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('obl.create.btn') }}</button>
      </div>

      <div class="card">
        <div class="ch"><h3>{{ $t('obl.list') }}</h3><span class="cnt">{{ obligations.length }}</span></div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 900px">
            <thead><tr>
              <th>{{ $t('obl.th.code') }}</th><th>{{ $t('obl.th.title') }}</th><th>{{ $t('obl.th.source') }}</th>
              <th>{{ $t('obl.th.dept') }}</th><th>{{ $t('obl.th.due') }}</th><th>{{ $t('obl.th.status') }}</th><th>{{ $t('obl.th.op') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="o in obligations" :key="o.id">
                <td class="code">{{ o.code }}</td>
                <td>{{ o.title }}</td>
                <td class="muted">{{ o.sourceRef || '—' }}</td>
                <td>{{ o.ownerDept || '—' }}</td>
                <td class="num">{{ o.dueDate || '—' }}</td>
                <td><span class="st" :class="stCls(o.status)"><span class="d"></span>{{ $t('obl.status.' + o.status) }}</span></td>
                <td class="ops">
                  <button v-if="o.status === 'PENDING' || o.status === 'NON_COMPLIANT'" class="btn ghost sm" :disabled="busyId === o.id" @click="act(o, 'start')">{{ $t('obl.op.start') }}</button>
                  <button v-if="o.status === 'IN_PROGRESS'" class="btn sm" :disabled="busyId === o.id" @click="openFulfill(o)">{{ $t('obl.op.fulfill') }}</button>
                  <button v-if="o.status === 'PENDING' || o.status === 'IN_PROGRESS'" class="btn ghost sm danger" :disabled="busyId === o.id" @click="act(o, 'non-compliant')">{{ $t('obl.op.nc') }}</button>
                </td>
              </tr>
              <tr v-if="!obligations.length"><td colspan="7" class="emptyrow">{{ loadError || $t('obl.empty') }}</td></tr>
            </tbody>
          </table>
        </div>
        <p v-if="opError" class="cerr" style="padding: 0 18px 12px">{{ opError }}</p>
      </div>

      <!-- 登记义务弹窗 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('obl.create.btn') }}</h3>
          <label class="fld">{{ $t('obl.th.code') }}<input v-model="cf.code" /></label>
          <label class="fld">{{ $t('obl.th.title') }}<input v-model="cf.title" /></label>
          <label class="fld">{{ $t('obl.create.source') }}<input v-model="cf.sourceRef" :placeholder="$t('obl.create.sourcePh')" /></label>
          <label class="fld">{{ $t('obl.th.dept') }}<input v-model="cf.ownerDept" /></label>
          <label class="fld">{{ $t('obl.th.due') }}<input v-model="cf.dueDate" type="date" /></label>
          <label class="fld">{{ $t('obl.create.org') }}
            <select v-model.number="cf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!cf.code || !cf.title || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('obl.create.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 落实弹窗（证据必填）-->
      <div v-if="showFulfill" class="modal-mask" @click.self="showFulfill = false">
        <div class="modal-card">
          <h3>{{ $t('obl.fulfill.title') }}</h3>
          <p class="muted" style="margin: -6px 0 14px; font-size: 12.5px">{{ fulfillTarget && fulfillTarget.title }}</p>
          <label class="fld">{{ $t('obl.fulfill.evidence') }}<input v-model="ff.evidence" :placeholder="$t('obl.fulfill.evidencePh')" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showFulfill = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!ff.evidence || saving" @click="submitFulfill">{{ saving ? $t('common.submitting') : $t('obl.fulfill.ok') }}</button>
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
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()
import { canWrite } from '@/auth.js'

const obligations = ref([])
const loadError = ref('')
const saving = ref(false)
const opError = ref('')
const busyId = ref(null)

async function load() {
  loadError.value = ''
  try { obligations.value = await api.get('/obligations') } catch (e) { loadError.value = e.message; obligations.value = [] }
}
const stCls = (s) => ({ PENDING: 'wait', IN_PROGRESS: 'doing', FULFILLED: 'ok', NON_COMPLIANT: 'over' }[s] || 'wait')

async function act(o, op) {
  busyId.value = o.id; opError.value = ''
  try { await api.post('/obligations/' + o.id + '/' + op, {}); await load() }
  catch (e) { opError.value = e.message } finally { busyId.value = null }
}

// 登记
const showCreate = ref(false)
const cf = reactive({ code: '', title: '', sourceRef: '', ownerDept: '', dueDate: '', orgId: 12 })
function openCreate() { Object.assign(cf, { code: '', title: '', sourceRef: '', ownerDept: '', dueDate: '', orgId: 12 }); opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/obligations', { orgId: cf.orgId, code: cf.code, title: cf.title, sourceRef: cf.sourceRef, ownerDept: cf.ownerDept, dueDate: cf.dueDate || null })
    showCreate.value = false; await load()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 落实（证据必填）
const showFulfill = ref(false)
const fulfillTarget = ref(null)
const ff = reactive({ evidence: '' })
function openFulfill(o) { fulfillTarget.value = o; ff.evidence = ''; opError.value = ''; showFulfill.value = true }
async function submitFulfill() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/obligations/' + fulfillTarget.value.id + '/fulfill', { evidence: ff.evidence })
    showFulfill.value = false; await load()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

onMounted(load)
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.ghost.danger { color: var(--danger); border-color: var(--danger); }
.btn.sm { padding: 4px 10px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.cb { padding: 14px 18px 18px; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 14px 10px; }
tbody td { padding: 9px 14px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
td.ops { display: flex; gap: 6px; }
.num { font-variant-numeric: tabular-nums; }
.code { font-weight: 700; color: var(--accent-strong); }
.muted { color: var(--text-3); font-size: 11.5px; }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.doing { color: var(--accent-strong); } .st.doing .d { background: var(--accent); }
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
