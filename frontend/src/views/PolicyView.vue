<template>
  <!--
    制度发布（PolicyView · M1）：制度全生命周期 + 审批两步化。
    功能真源 = 后端 /api/policies；视觉遵 tokens.css。审批红线：提交评审(DRAFT→REVIEW)经 Flowable 审批，
    审批通过才生效(REVIEW→EFFECTIVE)，仅 EFFECTIVE 可签署/废止。隔离由后端 RLS。
  -->
  <AppShell>
    <section class="view view-policy">
      <div class="phead">
        <div><div class="kqt">{{ $t('policy.tag') }}</div><h1>{{ $t('policy.title') }}</h1></div>
        <div class="sp"></div>
        <button class="btn" :disabled="!canWrite('policy.create')" :title="canWrite('policy.create') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('policy.create.btn') }}</button>
      </div>

      <div class="card">
        <div class="ch"><h3>{{ $t('policy.list') }}</h3><span class="cnt">{{ policies.length }}</span></div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 760px">
            <thead><tr>
              <th>{{ $t('policy.th.code') }}</th><th>{{ $t('policy.th.title') }}</th>
              <th>{{ $t('policy.th.version') }}</th><th>{{ $t('policy.th.status') }}</th><th>{{ $t('policy.th.op') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="p in policies" :key="p.id">
                <td class="code">{{ p.code }}</td>
                <td>{{ p.title }}</td>
                <td class="num">v{{ p.version }}</td>
                <td><span class="st" :class="ST_CLS[p.status]"><span class="d"></span>{{ $t('policy.status.' + p.status) }}</span></td>
                <td class="ops">
                  <button v-if="p.status === 'DRAFT'" class="btn sm" :disabled="busyId === p.id" @click="act(p, 'submit')">{{ $t('policy.op.submit') }}</button>
                  <template v-else-if="p.status === 'REVIEW'">
                    <button class="btn sm" :disabled="busyId === p.id" @click="act(p, 'approve')">{{ $t('policy.op.approve') }}</button>
                    <button class="btn ghost sm danger" :disabled="busyId === p.id" @click="openReject(p)">{{ $t('policy.op.reject') }}</button>
                  </template>
                  <template v-else-if="p.status === 'EFFECTIVE'">
                    <button class="btn ghost sm" :disabled="busyId === p.id" @click="act(p, 'signoff')">{{ $t('policy.op.signoff') }}</button>
                    <button class="btn ghost sm danger" :disabled="busyId === p.id" @click="act(p, 'archive')">{{ $t('policy.op.archive') }}</button>
                  </template>
                  <span v-else class="muted">{{ $t('policy.deprecated') }}</span>
                </td>
              </tr>
              <tr v-if="!policies.length"><td colspan="5" class="emptyrow">{{ loadError || $t('policy.empty') }}</td></tr>
            </tbody>
          </table>
        </div>
        <p v-if="opError" class="cerr" style="padding: 0 18px 12px">{{ opError }}</p>
      </div>

      <!-- 新建制度弹窗 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('policy.create.btn') }}</h3>
          <label class="fld">{{ $t('policy.th.code') }}<input v-model="cf.code" placeholder="POL-KYC-001" /></label>
          <label class="fld">{{ $t('policy.th.title') }}<input v-model="cf.title" /></label>
          <label class="fld">{{ $t('policy.create.content') }}<input v-model="cf.content" /></label>
          <label class="fld">{{ $t('policy.create.org') }}
            <select v-model.number="cf.orgId"><option :value="12">{{ $t('policy.org.pay') }}</option><option :value="13">{{ $t('policy.org.consumer') }}</option></select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!cf.code || !cf.title || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('policy.create.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 驳回弹窗（原因）-->
      <div v-if="showReject" class="modal-mask" @click.self="showReject = false">
        <div class="modal-card">
          <h3>{{ $t('policy.reject.title') }}</h3>
          <p class="muted" style="margin: -6px 0 14px; font-size: 12.5px">{{ rejectTarget && rejectTarget.title }}</p>
          <label class="fld">{{ $t('policy.reject.reason') }}<input v-model="rejectReason" :placeholder="$t('policy.reject.reasonPh')" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showReject = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!rejectReason || saving" @click="submitReject">{{ saving ? $t('common.submitting') : $t('policy.op.reject') }}</button>
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
import { canWrite } from '@/auth.js'

const policies = ref([])
const loadError = ref('')
const saving = ref(false)
const opError = ref('')
const busyId = ref(null)
const ST_CLS = { DRAFT: 'wait', REVIEW: 'doing', EFFECTIVE: 'ok', DEPRECATED: 'over' }

async function load() {
  loadError.value = ''
  try { policies.value = await api.get('/policies') } catch (e) { loadError.value = e.message; policies.value = [] }
}
// 一键流转：submit/approve/signoff/archive（驳回走单独弹窗带原因）
async function act(p, op) {
  busyId.value = p.id; opError.value = ''
  try { await api.post('/policies/' + p.id + '/' + op, {}); await load() }
  catch (e) { opError.value = e.message } finally { busyId.value = null }
}

// 新建
const showCreate = ref(false)
const cf = reactive({ code: '', title: '', content: '', orgId: 12 })
function openCreate() { Object.assign(cf, { code: '', title: '', content: '', orgId: 12 }); opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try { await api.post('/policies', { orgId: cf.orgId, code: cf.code, title: cf.title, content: cf.content }); showCreate.value = false; await load() }
  catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 驳回（带原因）
const showReject = ref(false)
const rejectTarget = ref(null)
const rejectReason = ref('')
function openReject(p) { rejectTarget.value = p; rejectReason.value = ''; opError.value = ''; showReject.value = true }
async function submitReject() {
  saving.value = true; opError.value = ''
  try { await api.post('/policies/' + rejectTarget.value.id + '/reject', { reason: rejectReason.value }); showReject.value = false; await load() }
  catch (e) { opError.value = e.message } finally { saving.value = false }
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
td.ops { display: flex; gap: 6px; align-items: center; }
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
