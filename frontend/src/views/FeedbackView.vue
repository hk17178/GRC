<template>
  <!--
    建议与反馈（FeedbackView）：反馈生命周期。
    功能真源 = 后端 /api/feedback；视觉遵 tokens.css。办结闭环红线：办结须填处置结果（办结弹窗必填）。
  -->
  <AppShell>
    <section class="view view-fb">
      <div class="phead">
        <div><div class="kqt">{{ $t('fb.tag') }}</div><h1>{{ $t('fb.title') }}</h1></div>
        <div class="sp"></div>
        <button class="btn" @click="openCreate">{{ $t('fb.create.btn') }}</button>
      </div>

      <div class="card">
        <div class="ch"><h3>{{ $t('fb.list') }}</h3><span class="cnt">{{ items.length }}</span></div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 800px">
            <thead><tr>
              <th>{{ $t('fb.th.type') }}</th><th>{{ $t('fb.th.title') }}</th><th>{{ $t('fb.th.submitter') }}</th>
              <th>{{ $t('fb.th.handler') }}</th><th>{{ $t('fb.th.status') }}</th><th>{{ $t('fb.th.op') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="f in items" :key="f.id">
                <td><span class="pill">{{ $t('fb.type.' + f.type) }}</span></td>
                <td>{{ f.title }}</td>
                <td>{{ f.submitter || '—' }}</td>
                <td>{{ f.handler || '—' }}</td>
                <td><span class="st" :class="stCls(f.status)"><span class="d"></span>{{ $t('fb.status.' + f.status) }}</span></td>
                <td class="ops">
                  <button v-if="f.status === 'SUBMITTED'" class="btn ghost sm" :disabled="busyId === f.id" @click="openTriage(f)">{{ $t('fb.op.triage') }}</button>
                  <button v-if="f.status === 'IN_PROGRESS'" class="btn sm" :disabled="busyId === f.id" @click="openResolve(f)">{{ $t('fb.op.resolve') }}</button>
                  <button v-if="f.status === 'RESOLVED'" class="btn ghost sm" :disabled="busyId === f.id" @click="act(f, 'close')">{{ $t('fb.op.close') }}</button>
                  <button v-if="f.status === 'SUBMITTED' || f.status === 'IN_PROGRESS'" class="btn ghost sm danger" :disabled="busyId === f.id" @click="act(f, 'reject')">{{ $t('fb.op.reject') }}</button>
                </td>
              </tr>
              <tr v-if="!items.length"><td colspan="6" class="emptyrow">{{ loadError || $t('fb.empty') }}</td></tr>
            </tbody>
          </table>
        </div>
        <p v-if="opError" class="cerr" style="padding: 0 18px 12px">{{ opError }}</p>
      </div>

      <!-- 提交反馈 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('fb.create.btn') }}</h3>
          <label class="fld">{{ $t('fb.th.type') }}
            <select v-model="cf.type"><option value="SUGGESTION">{{ $t('fb.type.SUGGESTION') }}</option><option value="COMPLAINT">{{ $t('fb.type.COMPLAINT') }}</option><option value="BUG">{{ $t('fb.type.BUG') }}</option><option value="QUESTION">{{ $t('fb.type.QUESTION') }}</option></select>
          </label>
          <label class="fld">{{ $t('fb.th.title') }}<input v-model="cf.title" /></label>
          <label class="fld">{{ $t('fb.create.content') }}<input v-model="cf.content" /></label>
          <label class="fld">{{ $t('fb.create.org') }}
            <select v-model.number="cf.orgId"><option :value="12">{{ $t('fb.org.pay') }}</option><option :value="13">{{ $t('fb.org.consumer') }}</option></select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!cf.title || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('fb.create.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 受理 / 办结 弹窗（共用，按 mode）-->
      <div v-if="showAct" class="modal-mask" @click.self="showAct = false">
        <div class="modal-card">
          <h3>{{ actMode === 'triage' ? $t('fb.op.triage') : $t('fb.resolve.title') }}</h3>
          <p class="muted" style="margin: -6px 0 14px; font-size: 12.5px">{{ actTarget && actTarget.title }}</p>
          <label class="fld">{{ actMode === 'triage' ? $t('fb.triage.handler') : $t('fb.resolve.resolution') }}
            <input v-model="actText" :placeholder="actMode === 'triage' ? $t('fb.triage.handlerPh') : $t('fb.resolve.resolutionPh')" />
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAct = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!actText || saving" @click="submitAct">{{ saving ? $t('common.submitting') : $t('common.confirm') }}</button>
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

const items = ref([])
const loadError = ref('')
const saving = ref(false)
const opError = ref('')
const busyId = ref(null)

async function load() {
  loadError.value = ''
  try { items.value = await api.get('/feedback') } catch (e) { loadError.value = e.message; items.value = [] }
}
const stCls = (s) => ({ SUBMITTED: 'wait', IN_PROGRESS: 'doing', RESOLVED: 'ok', CLOSED: 'ok', REJECTED: 'over' }[s] || 'wait')

async function act(f, op) {
  busyId.value = f.id; opError.value = ''
  try { await api.post('/feedback/' + f.id + '/' + op, {}); await load() }
  catch (e) { opError.value = e.message } finally { busyId.value = null }
}

// 提交
const showCreate = ref(false)
const cf = reactive({ type: 'SUGGESTION', title: '', content: '', orgId: 12 })
function openCreate() { Object.assign(cf, { type: 'SUGGESTION', title: '', content: '', orgId: 12 }); opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try { await api.post('/feedback', { orgId: cf.orgId, type: cf.type, title: cf.title, content: cf.content }); showCreate.value = false; await load() }
  catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 受理/办结
const showAct = ref(false)
const actMode = ref('triage')
const actTarget = ref(null)
const actText = ref('')
function openTriage(f) { actMode.value = 'triage'; actTarget.value = f; actText.value = ''; opError.value = ''; showAct.value = true }
function openResolve(f) { actMode.value = 'resolve'; actTarget.value = f; actText.value = ''; opError.value = ''; showAct.value = true }
async function submitAct() {
  saving.value = true; opError.value = ''
  try {
    if (actMode.value === 'triage') await api.post('/feedback/' + actTarget.value.id + '/triage', { handler: actText.value })
    else await api.post('/feedback/' + actTarget.value.id + '/resolve', { resolution: actText.value })
    showAct.value = false; await load()
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
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: var(--info-tint); color: var(--info); }
.muted { color: var(--text-3); font-size: 12.5px; }
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
