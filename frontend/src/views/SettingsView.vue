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
        <button class="btn" :disabled="!canWrite('settings')" :title="canWrite('settings') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('set.create.btn') }}</button>
      </div>

      <!-- ⑥ 登录页与品牌（全局，登录前生效；补回 UAT 反馈缺失项）-->
      <div class="card" style="margin-bottom: 14px">
        <div class="ch"><h3>登录页与品牌</h3><span class="sub">全局生效 · 登录前可见</span></div>
        <div class="cb">
          <div class="bgrid">
            <label class="fld">平台名称<input v-model="brand.brandName" placeholder="如 曼道集团 GRC" /></label>
            <label class="fld">平台副名<input v-model="brand.brandSub" placeholder="治理·风险·合规" /></label>
            <label class="fld">Logo 字符<input v-model="brand.logoText" maxlength="4" placeholder="如 曼 / G（无图时显示）" /></label>
            <label class="fld">Logo 图片 URL<input v-model="brand.logoImg" placeholder="https://…/logo.png（优先于字符，可留空）" /></label>
            <label class="fld">忘记密码链接<input v-model="brand.forgotUrl" placeholder="https://…（可留空）" /></label>
            <label class="fld">登录主标题（中）<textarea v-model="brand.loginTitleZh" rows="2" placeholder="可用换行；留空用默认"></textarea></label>
            <label class="fld">登录主标题（英）<textarea v-model="brand.loginTitleEn" rows="2"></textarea></label>
            <label class="fld">登录标语（中）<input v-model="brand.loginSloganZh" placeholder="留空用默认" /></label>
            <label class="fld">登录标语（英）<input v-model="brand.loginSloganEn" /></label>
          </div>
          <div class="bacts">
            <button class="btn" :disabled="!canWrite('settings') || brandSaving" @click="saveBranding">{{ brandSaving ? '保存中…' : '保存品牌配置' }}</button>
            <span v-if="brandMsg" class="ok-msg">{{ brandMsg }}</span>
            <span v-if="brandErr" class="cerr">{{ brandErr }}</span>
            <span class="muted" style="margin-left:auto">改后刷新登录页即生效</span>
          </div>
        </div>
      </div>

      <!-- 可改配置：按分类分组的紧凑卡片（不再是横铺满屏的稀疏宽表）-->
      <div class="groups">
        <div v-for="g in editableGroups" :key="g.cat" class="card grp">
          <div class="ch"><h3>{{ g.label }}</h3><span class="cnt">{{ g.items.length }}</span></div>
          <div class="cb glist">
            <div v-for="s in g.items" :key="s.id" class="srow">
              <div class="si">
                <div class="s-name">{{ s.description || humanizeKey(s.settingKey) }}</div>
                <div class="s-key"><code>{{ s.settingKey }}</code> <span class="pill">{{ s.valueType }}</span></div>
              </div>
              <div class="sv" :title="fmtVal(s)">{{ fmtVal(s) }}</div>
              <button class="btn ghost sm" :disabled="!canWrite('settings')" :title="canWrite('settings') ? '' : $t('common.noPerm')" @click="openEdit(s)">{{ $t('set.op.edit') }}</button>
            </div>
          </div>
        </div>
        <div v-if="!editableGroups.length && !lockedSettings.length" class="card">
          <div class="cb"><p class="muted" style="text-align:center;padding:12px">{{ loadError || $t('set.empty') }}</p></div>
        </div>
      </div>

      <!-- 系统基线参数（只读·合规固化）：实装但按监管/一致性要求锁定，单独成区并用大白话解释 -->
      <div v-if="lockedSettings.length" class="card baseline">
        <div class="ch"><h3>🔒 系统基线参数</h3><span class="sub">只读 · 合规固化，不开放运行期修改（调整走版本升级）</span></div>
        <div class="cb">
          <div v-for="s in lockedSettings" :key="s.id" class="brow">
            <div class="bl">
              <div class="s-name">{{ s.description || humanizeKey(s.settingKey) }}</div>
              <div class="bnote">{{ LOCK_NOTE[s.settingKey] || '' }}</div>
              <div class="s-key"><code>{{ s.settingKey }}</code></div>
            </div>
            <div class="bval">{{ fmtLockedVal(s) }}</div>
          </div>
        </div>
      </div>
      <p v-if="opError" class="cerr" style="padding: 10px 2px 0">{{ opError }}</p>

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
            <select v-model.number="cf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select>
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
import { ref, reactive, computed, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()
import { canWrite } from '@/auth.js'

const settings = ref([])
// #5 让键值对更友好：分类中文标签 + 键名兜底人性化 + 布尔/JSON 取值可读化
const CAT_LABEL = { compliance: '合规留存', security: '安全', risk: '风险', ai: 'AI 接入', branding: '品牌外观',
  scheduler: '调度', notify: '通知', system: '系统', general: '通用' }
function humanizeKey(k) { return (k || '').replace(/[._-]/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()) }
function fmtVal(s) {
  if (s.valueType === 'BOOL') return s.settingValue === 'true' ? '是（开启）' : '否（关闭）'
  if (s.valueType === 'JSON') return s.settingValue.length > 60 ? s.settingValue.slice(0, 60) + '…' : s.settingValue
  return s.settingValue
}
// 分类展示顺序（未列出的排最后）
const CAT_ORDER = ['security', 'compliance', 'risk', 'notify', 'scheduler', 'ai', 'branding', 'system', 'general']
// 可改配置按分类分组（只读锁定项单独成区，不混进来）
const editableGroups = computed(() => {
  const byCat = {}
  for (const s of settings.value) {
    if (!s.editable) continue
    const c = s.category || 'general'
    ;(byCat[c] = byCat[c] || []).push(s)
  }
  const rank = (c) => { const i = CAT_ORDER.indexOf(c); return i < 0 ? 99 : i }
  return Object.keys(byCat).sort((a, b) => rank(a) - rank(b))
    .map((c) => ({ cat: c, label: CAT_LABEL[c] || c, items: byCat[c] }))
})
const lockedSettings = computed(() => settings.value.filter((s) => !s.editable))
// 锁定项的大白话说明（为何存在、为何锁定）——避免用户以为是坏掉的灰控件
const LOCK_NOTE = {
  'hashchain.algo': '操作留痕哈希链所用的摘要算法，是防篡改证据链的根基。全平台统一，系统固定、不开放运行期修改。',
  'retention.operation-log.years': '操作留痕（谁、何时、改了什么）至少保存的年限。支付机构监管口径不低于 5 年，系统固定、不开放运行期修改。',
  'retention.evidence.years': '上传证据原件至少保存的年限，与操作留痕同口径。系统固定、不开放运行期修改。',
  'risk.matrix.bands': '风险打分（可能性 × 影响 的乘积）落到五个等级的分界线，全平台统一用这一套。改动需走版本升级，运行期不开放。'
}
// 锁定项取值的可读化：年限带单位、风险档位 JSON 转「≤4 极低 · …」
function fmtLockedVal(s) {
  if (s.settingKey === 'risk.matrix.bands') {
    try {
      const L = { VERY_LOW: '极低', LOW: '低', MID: '中', HIGH: '高', VERY_HIGH: '极高' }
      return JSON.parse(s.settingValue).map((b) => `≤${b.max} ${L[b.level] || b.level}`).join(' · ')
    } catch { return s.settingValue }
  }
  if (s.settingKey.endsWith('.years')) return s.settingValue + ' 年'
  return fmtVal(s)
}
const loadError = ref('')
const saving = ref(false)
const opError = ref('')

async function load() {
  loadError.value = ''
  try { settings.value = await api.get('/settings') } catch (e) { loadError.value = e.message; settings.value = [] }
}

// ⑥ 登录页与品牌配置（/api/branding，全局）
const brand = reactive({ brandName: '', brandSub: '', logoText: '', logoImg: '', loginTitleZh: '', loginTitleEn: '', loginSloganZh: '', loginSloganEn: '', forgotUrl: '' })
const brandSaving = ref(false)
const brandMsg = ref('')
const brandErr = ref('')
async function loadBranding() {
  try {
    const b = await api.get('/branding') || {}
    for (const k of Object.keys(brand)) brand[k] = b[k] || ''
  } catch (e) { /* 保持空 */ }
}
async function saveBranding() {
  brandSaving.value = true; brandMsg.value = ''; brandErr.value = ''
  try {
    // 空串转 null（后端为空时前端回退 i18n 默认）
    const payload = {}
    for (const k of Object.keys(brand)) payload[k] = brand[k] ? brand[k] : null
    await api.put('/branding', payload)
    brandMsg.value = '已保存'
    setTimeout(() => (brandMsg.value = ''), 2500)
  } catch (e) { brandErr.value = e.message } finally { brandSaving.value = false }
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

onMounted(() => { load(); loadBranding() })
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
/* ⑥ 登录页与品牌 */
.ch .sub { font-size: 11px; color: var(--text-3); margin-left: 4px; }
.bgrid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 760px) { .bgrid { grid-template-columns: 1fr; } }
.bgrid .fld { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-2); }
.bgrid .fld input, .bgrid .fld textarea { padding: 8px 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13px; font-family: inherit; outline: none; box-sizing: border-box; }
.bacts { display: flex; align-items: center; gap: 12px; margin-top: 14px; }
.ok-msg { color: var(--success); font-weight: 600; font-size: 12px; }
/* #5 配置项友好展示 */
.s-name { font-size: 13px; font-weight: 600; color: var(--text-1); }
.s-key { margin-top: 3px; display: flex; align-items: center; gap: 6px; }
.s-key code { font-size: 10.5px; color: var(--text-3); }
.cat-pill { display: inline-block; padding: 2px 9px; border-radius: 999px; font-size: 11px; font-weight: 600; background: var(--accent-weak); color: var(--accent-strong); }
/* ①② 分组卡片布局：宽屏多列，不再横铺满屏的稀疏宽表 */
.groups { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 14px; align-items: start; }
.grp .cb.glist { padding: 4px 16px 10px; }
.srow { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-top: 1px solid var(--border-subtle); }
.srow:first-child { border-top: 0; }
.srow .si { flex: 1; min-width: 0; }
.srow .sv { font-family: var(--font-mono, monospace); font-size: 12px; color: var(--text-1); max-width: 34%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.srow .btn.sm { flex-shrink: 0; }
/* 系统基线参数（只读·合规固化） */
.baseline { margin-top: 16px; background: linear-gradient(180deg, var(--accent-weak), var(--surface) 60%); }
.baseline .ch .sub { font-size: 11px; color: var(--text-3); margin-left: 4px; }
.brow { display: flex; align-items: flex-start; gap: 18px; padding: 13px 0; border-top: 1px dashed var(--border-subtle); }
.brow:first-child { border-top: 0; }
.brow .bl { flex: 1; min-width: 0; }
.brow .bnote { font-size: 12px; color: var(--text-2); margin: 4px 0 5px; line-height: 1.55; }
.brow .bval { font-family: var(--font-mono, monospace); font-size: 12.5px; font-weight: 700; color: var(--accent-strong); text-align: right; max-width: 44%; line-height: 1.5; }
</style>
