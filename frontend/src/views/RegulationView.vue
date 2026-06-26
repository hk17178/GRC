<template>
  <!--
    法规跟踪（RegulationView）：法规库 + 变更动态 + 影响评估闭环。
    功能真源 = 后端 /api/regulations（库/状态/变更/影响评估）；视觉遵 tokens.css。隔离由后端 RLS。
  -->
  <AppShell>
    <section class="view view-reg">
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('reg.tag') }}</div>
          <h1>{{ $t('reg.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn" :disabled="!canWrite('law')" :title="canWrite('law') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('reg.create.btn') }}</button>
      </div>

      <div class="g">
        <!-- 法规库 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('reg.lib') }}</h3><span class="cnt">{{ regulations.length }}</span></div>
          <div class="cb" style="overflow-x: auto; padding-top: 0">
            <table style="min-width: 520px">
              <thead><tr>
                <th>{{ $t('reg.th.code') }}</th><th>{{ $t('reg.th.title') }}</th>
                <th>{{ $t('reg.th.issuer') }}</th><th>{{ $t('reg.th.status') }}</th>
              </tr></thead>
              <tbody>
                <tr v-for="r in regulations" :key="r.id" class="clk" :class="{ on: r.id === selectedId }" @click="selectReg(r)">
                  <td class="code">{{ r.code }}</td>
                  <td>{{ r.title }}</td>
                  <td>{{ r.issuer || '—' }}</td>
                  <td><span class="st" :class="regStCls(r.status)"><span class="d"></span>{{ $t('reg.status.' + r.status) }}</span></td>
                </tr>
                <tr v-if="!regulations.length"><td colspan="4" class="emptyrow">{{ loadError || $t('reg.empty') }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 变更动态（选中法规）-->
        <div class="card">
          <div class="ch">
            <h3>{{ $t('reg.changes') }}</h3>
            <button v-if="selectedId" class="btn ghost sm" @click="openChange">{{ $t('reg.change.btn') }}</button>
          </div>
          <div class="cb" style="overflow-x: auto; padding-top: 0">
            <div v-if="!selectedId" class="hint">{{ $t('reg.selectHint') }}</div>
            <table v-else style="min-width: 560px">
              <thead><tr>
                <th>{{ $t('reg.cth.type') }}</th><th>{{ $t('reg.cth.date') }}</th>
                <th>{{ $t('reg.cth.desc') }}</th><th>{{ $t('reg.cth.impact') }}</th><th>{{ $t('reg.cth.op') }}</th>
              </tr></thead>
              <tbody>
                <tr v-for="c in changes" :key="c.id">
                  <td><span class="pill">{{ $t('reg.changeType.' + c.changeType) }}</span></td>
                  <td class="num">{{ c.changeDate || '—' }}</td>
                  <td class="desc">{{ c.description }}</td>
                  <td><span class="st" :class="c.impactStatus === 'ASSESSED' ? 'ok' : 'wait'"><span class="d"></span>{{ $t('reg.impact.' + c.impactStatus) }}</span></td>
                  <td><button v-if="c.impactStatus === 'PENDING'" class="btn sm" @click="openAssess(c)">{{ $t('reg.assess.btn') }}</button></td>
                </tr>
                <tr v-if="!changes.length"><td colspan="5" class="emptyrow">{{ $t('reg.changeEmpty') }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 登记法规弹窗 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('reg.create.btn') }}</h3>
          <label class="fld">{{ $t('reg.th.code') }}<input v-model="cf.code" /></label>
          <label class="fld">{{ $t('reg.th.title') }}<input v-model="cf.title" /></label>
          <label class="fld">{{ $t('reg.th.issuer') }}<input v-model="cf.issuer" /></label>
          <label class="fld">{{ $t('reg.create.category') }}<input v-model="cf.category" /></label>
          <label class="fld">{{ $t('reg.create.org') }}
            <select v-model.number="cf.orgId"><option :value="12">{{ $t('reg.org.pay') }}</option><option :value="13">{{ $t('reg.org.consumer') }}</option></select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!cf.code || !cf.title || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('reg.create.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 登记变更弹窗 -->
      <div v-if="showChange" class="modal-mask" @click.self="showChange = false">
        <div class="modal-card">
          <h3>{{ $t('reg.change.btn') }}</h3>
          <label class="fld">{{ $t('reg.cth.type') }}
            <select v-model="ch.changeType"><option value="ENACTED">{{ $t('reg.changeType.ENACTED') }}</option><option value="AMENDED">{{ $t('reg.changeType.AMENDED') }}</option><option value="ABOLISHED">{{ $t('reg.changeType.ABOLISHED') }}</option></select>
          </label>
          <label class="fld">{{ $t('reg.cth.date') }}<input v-model="ch.changeDate" type="date" /></label>
          <label class="fld">{{ $t('reg.cth.desc') }}<input v-model="ch.description" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showChange = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!ch.description || saving" @click="submitChange">{{ saving ? $t('common.submitting') : $t('reg.change.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 影响评估弹窗 -->
      <div v-if="showAssess" class="modal-mask" @click.self="showAssess = false">
        <div class="modal-card">
          <h3>{{ $t('reg.assess.title') }}</h3>
          <label class="fld">{{ $t('reg.assess.scope') }}<input v-model="af.impactScope" :placeholder="$t('reg.assess.scopePh')" /></label>
          <label class="fld">{{ $t('reg.assess.note') }}<input v-model="af.impactNote" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAssess = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!af.impactScope || saving" @click="submitAssess">{{ saving ? $t('common.submitting') : $t('reg.assess.ok') }}</button>
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

const regulations = ref([])
const loadError = ref('')
const selectedId = ref(null)
const changes = ref([])
const saving = ref(false)
const opError = ref('')

async function loadRegs() {
  loadError.value = ''
  try { regulations.value = await api.get('/regulations') } catch (e) { loadError.value = e.message; regulations.value = [] }
}
async function selectReg(r) {
  selectedId.value = r.id
  try { changes.value = await api.get('/regulations/' + r.id + '/changes') } catch (e) { changes.value = [] }
}
const regStCls = (s) => ({ TRACKING: 'wait', EFFECTIVE: 'ok', SUPERSEDED: 'over', ABOLISHED: 'over' }[s] || 'wait')

// 登记法规
const showCreate = ref(false)
const cf = reactive({ code: '', title: '', issuer: '', category: '', orgId: 12 })
function openCreate() { Object.assign(cf, { code: '', title: '', issuer: '', category: '', orgId: 12 }); opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/regulations', { orgId: cf.orgId, code: cf.code, title: cf.title, issuer: cf.issuer, category: cf.category })
    showCreate.value = false; await loadRegs()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 登记变更
const showChange = ref(false)
const ch = reactive({ changeType: 'AMENDED', changeDate: '', description: '' })
function openChange() { Object.assign(ch, { changeType: 'AMENDED', changeDate: '', description: '' }); opError.value = ''; showChange.value = true }
async function submitChange() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/regulations/' + selectedId.value + '/changes', { changeType: ch.changeType, changeDate: ch.changeDate || null, description: ch.description })
    showChange.value = false; await selectReg({ id: selectedId.value })
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 影响评估
const showAssess = ref(false)
const assessTarget = ref(null)
const af = reactive({ impactScope: '', impactNote: '' })
function openAssess(c) { assessTarget.value = c; Object.assign(af, { impactScope: '', impactNote: '' }); opError.value = ''; showAssess.value = true }
async function submitAssess() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/regulations/changes/' + assessTarget.value.id + '/assess', { impactScope: af.impactScope, impactNote: af.impactNote })
    showAssess.value = false; await selectReg({ id: selectedId.value })
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

onMounted(loadRegs)
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.g { display: grid; grid-template-columns: 1fr 1.2fr; gap: 14px; }
@media (max-width: 980px) { .g { grid-template-columns: 1fr; } }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 4px 10px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.ch .btn.sm { margin-left: auto; }
.cb { padding: 14px 18px 18px; }
.hint { color: var(--text-3); font-size: 12.5px; padding: 18px; text-align: center; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 14px 10px; }
tbody td { padding: 9px 14px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
tbody tr.clk { cursor: pointer; }
tbody tr.clk:hover, tbody tr.on { background: var(--accent-tint); }
.num { font-variant-numeric: tabular-nums; }
.code { font-weight: 700; color: var(--accent-strong); }
.desc { color: var(--text-2); max-width: 240px; }
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: var(--info-tint); color: var(--info); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.over { color: var(--danger); } .st.over .d { background: var(--danger); }
.st.wait .d { background: var(--text-3); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 420px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0 0 12px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
</style>
