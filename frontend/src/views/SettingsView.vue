<template>
  <!--
    系统设置（SettingsView）：租户键值配置 / 可配置性。
    功能真源 = 后端 /api/settings；视觉遵 tokens.css。红线：系统锁定项(editable=false)不可改，无修改入口。
  -->
  <AppShell>
    <section class="view view-set">
      <div class="phead">
        <div><div class="kqt">{{ $t('set.tag') }}</div><h1>{{ $t('set.title') }}</h1></div>
        <div class="sp"></div>
        <button class="btn" @click="openCreate">{{ $t('set.create.btn') }}</button>
      </div>

      <div class="card">
        <div class="ch"><h3>{{ $t('set.list') }}</h3><span class="cnt">{{ settings.length }}</span></div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 820px">
            <thead><tr>
              <th>{{ $t('set.th.key') }}</th><th>{{ $t('set.th.category') }}</th><th>{{ $t('set.th.type') }}</th>
              <th>{{ $t('set.th.value') }}</th><th>{{ $t('set.th.editable') }}</th><th>{{ $t('set.th.op') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="s in settings" :key="s.id">
                <td class="code">{{ s.settingKey }}</td>
                <td class="muted">{{ s.category || '—' }}</td>
                <td><span class="pill">{{ s.valueType }}</span></td>
                <td class="val">{{ s.settingValue }}</td>
                <td>
                  <span v-if="s.editable" class="st ok"><span class="d"></span>{{ $t('set.editableYes') }}</span>
                  <span v-else class="st over"><span class="d"></span>🔒 {{ $t('set.locked') }}</span>
                </td>
                <td class="ops">
                  <button v-if="s.editable" class="btn ghost sm" @click="openEdit(s)">{{ $t('set.op.edit') }}</button>
                  <span v-else class="muted" :title="$t('set.lockTip')">{{ $t('set.lockedDash') }}</span>
                </td>
              </tr>
              <tr v-if="!settings.length"><td colspan="6" class="emptyrow">{{ loadError || $t('set.empty') }}</td></tr>
            </tbody>
          </table>
        </div>
        <p v-if="opError" class="cerr" style="padding: 0 18px 12px">{{ opError }}</p>
      </div>

      <!-- 定义配置 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('set.create.btn') }}</h3>
          <label class="fld">{{ $t('set.th.key') }}<input v-model="cf.key" /></label>
          <label class="fld">{{ $t('set.th.value') }}<input v-model="cf.value" /></label>
          <label class="fld">{{ $t('set.th.type') }}
            <select v-model="cf.valueType"><option value="STRING">STRING</option><option value="INT">INT</option><option value="BOOL">BOOL</option><option value="JSON">JSON</option></select>
          </label>
          <label class="fld">{{ $t('set.th.category') }}<input v-model="cf.category" /></label>
          <label class="fld chk"><input type="checkbox" v-model="cf.editable" /> {{ $t('set.create.editable') }}</label>
          <label class="fld">{{ $t('set.create.org') }}
            <select v-model.number="cf.orgId"><option :value="12">{{ $t('set.org.pay') }}</option><option :value="13">{{ $t('set.org.consumer') }}</option></select>
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!cf.key || saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('set.create.ok') }}</button>
          </div>
        </div>
      </div>

      <!-- 修改取值 -->
      <div v-if="showEdit" class="modal-mask" @click.self="showEdit = false">
        <div class="modal-card">
          <h3>{{ $t('set.edit.title') }}</h3>
          <p class="muted" style="margin: -6px 0 14px; font-size: 12.5px">{{ editTarget && editTarget.settingKey }}（{{ editTarget && editTarget.valueType }}）</p>
          <label class="fld">{{ $t('set.edit.value') }}<input v-model="editValue" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showEdit = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="saving" @click="submitEdit">{{ saving ? $t('common.submitting') : $t('set.edit.ok') }}</button>
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

const settings = ref([])
const loadError = ref('')
const saving = ref(false)
const opError = ref('')

async function load() {
  loadError.value = ''
  try { settings.value = await api.get('/settings') } catch (e) { loadError.value = e.message; settings.value = [] }
}

// 定义
const showCreate = ref(false)
const cf = reactive({ key: '', value: '', valueType: 'STRING', category: '', editable: true, orgId: 12 })
function openCreate() { Object.assign(cf, { key: '', value: '', valueType: 'STRING', category: '', editable: true, orgId: 12 }); opError.value = ''; showCreate.value = true }
async function submitCreate() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/settings', { orgId: cf.orgId, key: cf.key, value: cf.value, valueType: cf.valueType, category: cf.category, editable: cf.editable })
    showCreate.value = false; await load()
  } catch (e) { opError.value = e.message } finally { saving.value = false }
}

// 修改取值
const showEdit = ref(false)
const editTarget = ref(null)
const editValue = ref('')
function openEdit(s) { editTarget.value = s; editValue.value = s.settingValue || ''; opError.value = ''; showEdit.value = true }
async function submitEdit() {
  saving.value = true; opError.value = ''
  try {
    await api.post('/settings/' + editTarget.value.id + '/value', { value: editValue.value })
    showEdit.value = false; await load()
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
.code { font-weight: 700; color: var(--accent-strong); font-family: var(--font-mono, monospace); font-size: 11.5px; }
.val { font-family: var(--font-mono, monospace); font-size: 11.5px; }
.muted { color: var(--text-3); font-size: 11.5px; }
.pill { display: inline-block; padding: 2px 7px; border-radius: 6px; font-size: 10px; font-weight: 600; background: rgba(120,120,120,0.1); color: var(--text-2); font-family: var(--font-mono, monospace); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.over { color: var(--danger); } .st.over .d { background: var(--danger); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 420px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld.chk { display: flex; align-items: center; gap: 8px; }
.modal-card .fld input:not([type=checkbox]), .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
</style>
