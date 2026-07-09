<template>
  <!--
    合规清单（ObligationView · 八轮 8-3/B8 举证链改造）：
    满足结论由「举证链」派生只读（依据对象 + 证据），人工状态仅作落实过程记录；
    每条义务可点开「举证」查看/维护 法规条款→义务→制度/控制→评估/审计→证据 的链路。
    功能真源 = 后端 /api/obligations(/derived|/links)；视觉遵 tokens.css。
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

      <!-- 派生结论 KPI（真值：由举证链逐条派生） -->
      <div class="kpis">
        <div class="kpi"><label>义务总数</label><b>{{ rows.length }}</b></div>
        <div class="kpi"><label>已满足（链上有依据+证据）</label><b style="color: var(--success)">{{ cnt('MET') }}</b></div>
        <div class="kpi"><label>部分满足</label><b style="color: var(--warning)">{{ cnt('PARTIAL') }}</b></div>
        <div class="kpi"><label>缺口（无任何举证）</label><b style="color: var(--danger)">{{ cnt('GAP') }}</b></div>
      </div>

      <div class="card">
        <div class="ch"><h3>{{ $t('obl.list') }}</h3><span class="cnt">{{ rows.length }}</span>
          <span class="sub">满足结论由举证链派生，只读；点「举证」维护依据</span></div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 980px">
            <thead><tr>
              <th>{{ $t('obl.th.code') }}</th><th>{{ $t('obl.th.title') }}</th><th>{{ $t('obl.th.source') }}</th>
              <th>{{ $t('obl.th.dept') }}</th><th>{{ $t('obl.th.due') }}</th>
              <th>落实过程</th><th>派生结论</th><th>{{ $t('obl.th.op') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="r in rows" :key="r.obligation.id">
                <td class="code">{{ r.obligation.code }}</td>
                <td>{{ r.obligation.title }}</td>
                <td class="muted">{{ r.obligation.sourceRef || '—' }}</td>
                <td>{{ r.obligation.ownerDept || '—' }}</td>
                <td class="num">{{ r.obligation.dueDate || '—' }}</td>
                <td><span class="st" :class="stCls(r.obligation.status)"><span class="d"></span>{{ $t('obl.status.' + r.obligation.status) }}</span></td>
                <td>
                  <span class="verdict" :class="'vd-' + r.derivedStatus" @click="openLinks(r.obligation)" title="点击查看举证链">
                    {{ DERIVED_TXT[r.derivedStatus] }}（{{ r.linkCount }}链/{{ r.evidenceCount }}证）
                  </span>
                </td>
                <td class="ops">
                  <button class="btn ghost sm" @click="openLinks(r.obligation)">举证</button>
                  <button v-if="r.obligation.status === 'PENDING' || r.obligation.status === 'NON_COMPLIANT'" class="btn ghost sm" :disabled="busyId === r.obligation.id" @click="act(r.obligation, 'start')">{{ $t('obl.op.start') }}</button>
                  <button v-if="r.obligation.status === 'IN_PROGRESS'" class="btn sm" :disabled="busyId === r.obligation.id" @click="openFulfill(r.obligation)">{{ $t('obl.op.fulfill') }}</button>
                  <button v-if="r.obligation.status === 'PENDING' || r.obligation.status === 'IN_PROGRESS'" class="btn ghost sm danger" :disabled="busyId === r.obligation.id" @click="act(r.obligation, 'non-compliant')">{{ $t('obl.op.nc') }}</button>
                </td>
              </tr>
              <tr v-if="!rows.length"><td colspan="8" class="emptyrow">{{ loadError || $t('obl.empty') }}</td></tr>
            </tbody>
          </table>
        </div>
        <p v-if="opError" class="cerr" style="padding: 0 18px 12px">{{ opError }}</p>
      </div>

      <!-- 举证链弹层（八轮 8-3：依据明细 + 挂接/摘除） -->
      <div v-if="linksTarget" class="modal-mask" @click.self="linksTarget = null">
        <div class="modal-card" style="width: 560px">
          <h3>举证链 · {{ linksTarget.code }} {{ linksTarget.title }}</h3>
          <div class="linklist">
            <div v-for="l in links" :key="l.id" class="linkrow">
              <span class="pill">{{ REF_TXT[l.refType] || l.refType }}</span>
              <b>#{{ l.refId }}</b>
              <span class="muted">{{ l.note }}</span>
              <button v-if="canWrite('obligation')" class="mini-x" style="margin-left:auto" title="摘除" @click="removeLink(l)">✕</button>
            </div>
            <div v-if="!links.length" class="emptyrow" style="padding: 10px 0">
              尚无举证依据——挂接 制度/控制/评估/审计/证据 后，满足结论才会从「缺口」变化。
            </div>
          </div>
          <div v-if="canWrite('obligation')" class="linkadd">
            <select v-model="lf.refType" class="sel">
              <option value="POLICY">制度</option><option value="CONTROL">控制点</option>
              <option value="ASSESSMENT">评估</option><option value="AUDIT">审计</option>
              <option value="EVIDENCE">证据</option>
            </select>
            <input v-model.number="lf.refId" type="number" min="1" placeholder="对象编号 id" class="inp" style="width: 110px" />
            <input v-model="lf.note" placeholder="说明（如 对应制度第3章）" class="inp" style="flex:1" />
            <button class="btn sm" :disabled="!lf.refId || saving" @click="addLink">挂接</button>
          </div>
          <p v-if="opError" class="cerr" style="margin-top: 8px">{{ opError }}</p>
          <div class="modal-actions"><button class="btn ghost" @click="linksTarget = null">{{ $t('common.cancel') }}</button></div>
        </div>
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
import { confirm } from '@/composables/confirm'
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()
import { canWrite } from '@/auth.js'

const rows = ref([])
const loadError = ref('')
const saving = ref(false)
const opError = ref('')
const busyId = ref(null)

const DERIVED_TXT = { MET: '已满足', PARTIAL: '部分满足', GAP: '缺口' }
const REF_TXT = { POLICY: '制度', CONTROL: '控制点', ASSESSMENT: '评估', AUDIT: '审计', EVIDENCE: '证据' }
const cnt = (k) => rows.value.filter((r) => r.derivedStatus === k).length

async function load() {
  loadError.value = ''
  try { rows.value = await api.get('/obligations/derived') } catch (e) { loadError.value = e.message; rows.value = [] }
}
const stCls = (s) => ({ PENDING: 'wait', IN_PROGRESS: 'doing', FULFILLED: 'ok', NON_COMPLIANT: 'over' }[s] || 'wait')

async function act(o, op) {
  busyId.value = o.id; opError.value = ''
  try { await api.post('/obligations/' + o.id + '/' + op, {}); await load() }
  catch (e) { opError.value = e.message } finally { busyId.value = null }
}

// ===== 八轮 8-3：举证链弹层 =====
const linksTarget = ref(null)
const links = ref([])
const lf = reactive({ refType: 'POLICY', refId: null, note: '' })
async function openLinks(o) {
  linksTarget.value = o; opError.value = ''
  Object.assign(lf, { refType: 'POLICY', refId: null, note: '' })
  try { links.value = await api.get('/obligations/' + o.id + '/links') } catch (e) { links.value = [] }
}
async function addLink() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/obligations/' + linksTarget.value.id + '/links', { refType: lf.refType, refId: lf.refId, note: lf.note || null })
    await openLinks(linksTarget.value); await load()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}
async function removeLink(l) {
  if (!await confirm('摘除该举证依据？派生结论会同步回退。')) return
  try { await api.del('/obligations/links/' + l.id); await openLinks(linksTarget.value); await load() }
  catch (e) { opError.value = e.message }
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
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.kpi { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); padding: 12px 16px; box-shadow: var(--shadow-1); }
.kpi label { font-size: 11px; color: var(--text-3); display: block; margin-bottom: 4px; }
.kpi b { font-size: 20px; font-family: var(--font-display); }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.ch .sub { font-size: 11px; color: var(--text-3); margin-left: auto; }
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
.verdict { font-size: 11px; font-weight: 700; padding: 2px 9px; border-radius: 999px; cursor: pointer; white-space: nowrap; }
.vd-MET { color: var(--success); background: color-mix(in srgb, var(--success) 12%, transparent); }
.vd-PARTIAL { color: #a87d22; background: color-mix(in srgb, #a87d22 12%, transparent); }
.vd-GAP { color: var(--danger); background: var(--danger-tint, color-mix(in srgb, var(--danger) 12%, transparent)); }
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: var(--info-tint, var(--accent-weak)); color: var(--accent-strong); }
.linklist { max-height: 260px; overflow-y: auto; margin-bottom: 10px; }
.linkrow { display: flex; align-items: center; gap: 8px; padding: 7px 2px; border-bottom: 1px solid var(--border-subtle); font-size: 12.5px; }
.linkadd { display: flex; gap: 8px; align-items: center; }
.sel, .inp { height: 32px; padding: 0 9px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12.5px; font-family: inherit; outline: none; }
.mini-x { border: 0; background: none; color: var(--text-3); font-size: 11px; cursor: pointer; padding: 2px 6px; border-radius: 4px; }
.mini-x:hover { color: var(--danger); background: var(--danger-tint); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 420px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
</style>
