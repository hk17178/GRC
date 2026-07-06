<template>
  <!--
    通知中心（NotifyView · M10）：补回 UAT 反馈缺失的"通知场景 / 通知规则 / 通道管理"等配置。
    功能真源 = 后端 /api/notify/configs（场景/规则/通道，按 kind）+ /api/workbench/notifications（提醒记录）。
    视觉遵 tokens.css；隔离由后端 RLS。写操作 canWrite('notify') 门控。
  -->
  <AppShell>
    <section class="view view-wb">
      <div class="phead">
        <div><div class="kqt">{{ $t('notify.tag') }}</div><h1>{{ $t('notify.title') }}</h1></div>
        <div class="sp"></div>
        <button v-if="tab !== 'log'" class="btn" :disabled="!canWrite('notify')"
                :title="canWrite('notify') ? '' : $t('common.noPerm')" @click="openAdd">＋ 新建{{ KIND_LABEL[tab] }}</button>
        <button v-else class="btn ghost" @click="loadLog">{{ $t('notify.refresh') }}</button>
      </div>

      <div class="tabbar">
        <button :class="{ on: tab === 'scenario' }" @click="tab = 'scenario'">通知场景</button>
        <button :class="{ on: tab === 'rule' }" @click="tab = 'rule'">通知规则</button>
        <button :class="{ on: tab === 'channel' }" @click="tab = 'channel'">通道管理</button>
        <button :class="{ on: tab === 'log' }" @click="tab = 'log'">提醒记录</button>
        <button :class="{ on: tab === 'digest' }" @click="tab = 'digest'; loadDigest()">{{ $t('notify.digest.tab') }}</button>
        <button :class="{ on: tab === 'pref' }" @click="tab = 'pref'; loadPref()">订阅偏好</button>
      </div>

      <!-- 通知场景 -->
      <div v-show="tab === 'scenario'" class="card">
        <div class="ch"><h3>企微通知场景库</h3><span class="cnt">{{ scenarios.length }}</span>
          <span class="sub">触发条件 → 接收角色 → 内容要点 → 通道</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:760px">
            <thead><tr><th>场景</th><th>触发</th><th>接收角色/层级</th><th>内容要点</th><th>通道</th><th>状态</th><th></th></tr></thead>
            <tbody>
              <tr v-for="c in scenarios" :key="c.id">
                <td><b>{{ c.name }}</b></td>
                <td class="muted">{{ d(c).trigger || '—' }}</td>
                <td class="muted">{{ d(c).receiver || '—' }}</td>
                <td class="muted">{{ d(c).contentPoints || '—' }}</td>
                <td><span class="pill">{{ CH_LABEL[d(c).channel] || d(c).channel || '—' }}</span></td>
                <td>{{ statusCell(c) }}</td>
                <td class="ops">{{ '' }}<template v-if="canWrite('notify')">
                  <button class="mini" @click="toggle(c)">{{ c.enabled ? '停用' : '启用' }}</button>
                  <button class="mini danger" @click="del(c)">删</button>
                </template></td>
              </tr>
              <tr v-if="!scenarios.length"><td colspan="7" class="emptyrow">暂无通知场景，点「＋ 新建通知场景」。</td></tr>
            </tbody>
          </table>
          <div style="font-size:11px;color:var(--text-3);margin-top:10px">模板变量：{责任人}{任务编号}{对象}{来源发现}{截止}{剩余天数}{链接}，可在内容要点中引用。</div>
        </div>
      </div>

      <!-- 通知规则（六轮 #7：可执行规则引擎——数据源+条件+内容模板，内核每 15 分钟自动评估）-->
      <div v-show="tab === 'rule'" class="card">
        <div class="ch"><h3>通知规则</h3><span class="cnt">{{ rules.length }}</span>
          <span class="sub">数据源 + 条件 → 渲染内容模板 → 产告警（幂等去重）</span>
          <button class="btn ghost sm" style="margin-left:12px" :disabled="!canWrite('notify') || engineBusy" @click="runEngine">{{ engineBusy ? '评估中…' : '▶ 立即评估一轮' }}</button>
          <span v-if="engineMsg" class="ackd" style="margin-left:8px">{{ engineMsg }}</span>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:760px">
            <thead><tr><th>规则</th><th>数据源</th><th>条件</th><th>内容模板</th><th>状态</th><th></th></tr></thead>
            <tbody>
              <tr v-for="c in rules" :key="c.id">
                <td><b>{{ c.name }}</b></td>
                <td>
                  <span v-if="SRC[d(c).source]" class="pill">{{ SRC[d(c).source].label }}</span>
                  <span v-else class="tag h">旧格式（引擎不评估）</span>
                </td>
                <td class="muted" style="white-space:nowrap">{{ condText(c) }}</td>
                <td class="muted" style="max-width:320px">{{ d(c).template || '—' }}</td>
                <td>{{ statusCell(c) }}</td>
                <td class="ops"><template v-if="canWrite('notify')">
                  <button class="mini" @click="toggle(c)">{{ c.enabled ? '停用' : '启用' }}</button>
                  <button class="mini danger" @click="del(c)">删</button>
                </template></td>
              </tr>
              <tr v-if="!rules.length"><td colspan="6" class="emptyrow">暂无通知规则，点「＋ 新建通知规则」。</td></tr>
            </tbody>
          </table>
          <div style="font-size:11px;color:var(--text-3);margin-top:10px">
            引擎由调度内核每 15 分钟自动评估一轮；同一对象同一规则只告警一次（幂等台账）。触发结果见「提醒记录」。
          </div>
        </div>
      </div>

      <!-- 通道管理 -->
      <div v-show="tab === 'channel'" class="card">
        <div class="ch"><h3>通道管理</h3><span class="cnt">{{ channels.length }}</span><span class="sub">邮件 / 短信 / 企微</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:560px">
            <thead><tr><th>通道</th><th>类型</th><th>目标/机器人</th><th>状态</th><th></th></tr></thead>
            <tbody>
              <tr v-for="c in channels" :key="c.id">
                <td><b>{{ c.name }}</b></td>
                <td><span class="pill">{{ CH_LABEL[d(c).type] || d(c).type || '—' }}</span></td>
                <td class="muted">{{ d(c).target || '—' }}</td>
                <td>{{ statusCell(c) }}</td>
                <td class="ops"><template v-if="canWrite('notify')">
                  <button class="mini" @click="toggle(c)">{{ c.enabled ? '停用' : '启用' }}</button>
                  <button class="mini danger" @click="del(c)">删</button>
                </template></td>
              </tr>
              <tr v-if="!channels.length"><td colspan="5" class="emptyrow">暂无通道，点「＋ 新建通道」。</td></tr>
            </tbody>
          </table>
          <div style="font-size:11px;color:var(--text-3);margin-top:10px">
            第一批外推仅支持 企微群机器人（类型=企微，目标=webhook 地址）；规则引擎产出的新告警会自动推送并留痕。邮件/短信通道后续批次接入。
          </div>
        </div>
      </div>

      <!-- 发送留痕（八轮 8-1：通道外推的成功/失败记录，失败不重试、留痕即达标）-->
      <div v-show="tab === 'channel'" class="card" style="margin-top: 14px">
        <div class="ch"><h3>发送留痕</h3><span class="cnt">{{ sendLogs.length }}</span>
          <button class="btn ghost sm" style="margin-left:auto" @click="loadSendLogs">刷新</button></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:720px">
            <thead><tr><th>时间</th><th>通道</th><th>目标</th><th>内容</th><th>结果</th></tr></thead>
            <tbody>
              <tr v-for="s in sendLogs" :key="s.id">
                <td class="num">{{ fmtTime(s.createdAtMs) }}</td>
                <td><span class="pill">{{ CH_LABEL[s.channelType] || s.channelType }}</span></td>
                <td class="muted">{{ s.target }}</td>
                <td class="muted" style="max-width:280px">{{ s.message }}</td>
                <td>
                  <span v-if="s.success" class="ackd">✓ 已送达</span>
                  <span v-else class="tag h" :title="s.error">失败：{{ (s.error || '').slice(0, 40) }}</span>
                </td>
              </tr>
              <tr v-if="!sendLogs.length"><td colspan="5" class="emptyrow">暂无外推记录——配置企微通道并触发规则告警后在此留痕。</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 提醒记录（调度内核派发）-->
      <div v-show="tab === 'log'" class="card">
        <div class="ch"><h3>{{ $t('notify.listTitle') }}</h3><span class="cnt">{{ items.length }}</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:820px">
            <thead><tr><th>{{ $t('notify.th.time') }}</th><th>{{ $t('notify.th.event') }}</th><th>内容</th><th>{{ $t('notify.th.object') }}</th><th>{{ $t('notify.th.receipt') }}</th></tr></thead>
            <tbody>
              <tr v-for="n in items" :key="n.id">
                <td class="num">{{ fmtTime(n.createdAtMs) }}</td>
                <td><span class="evt">{{ n.eventType }}</span><span v-if="n.mergedCount > 1" class="merge" :title="$t('notify.mergeTip')">×{{ n.mergedCount }}</span></td>
                <td class="muted" style="max-width:340px">{{ n.message || '—' }}</td>
                <td class="code">{{ n.objectType }}:{{ n.objectId }}</td>
                <td>
                  <span v-if="n.readBy" class="ackd">✓ {{ n.readBy }} · {{ fmtTime(n.readAtMs) }}</span>
                  <button v-else class="mini" @click="ack(n)">{{ $t('notify.ack') }}</button>
                </td>
              </tr>
              <tr v-if="!items.length"><td colspan="5" class="emptyrow">{{ loadError || $t('notify.empty') }}</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 定期简报（近 N 天提醒按事件类型聚合）-->
      <div v-show="tab === 'digest'" class="card">
        <div class="ch"><h3>{{ $t('notify.digest.tab') }}</h3><span class="sub">{{ $t('notify.digest.sub', { d: digestDays }) }}</span>
          <select class="sel" v-model.number="digestDays" @change="loadDigest" style="margin-left:auto">
            <option :value="7">{{ $t('notify.digest.d7') }}</option><option :value="30">{{ $t('notify.digest.d30') }}</option><option :value="90">{{ $t('notify.digest.d90') }}</option>
          </select>
        </div>
        <div class="cb" style="padding-top:0">
          <table style="min-width:520px">
            <thead><tr><th>{{ $t('notify.digest.event') }}</th><th>{{ $t('notify.digest.total') }}</th><th>{{ $t('notify.digest.unread') }}</th></tr></thead>
            <tbody>
              <tr v-for="r in digestRows" :key="r.eventType">
                <td><span class="evt">{{ r.eventType }}</span></td>
                <td class="num">{{ r.total }}</td>
                <td class="num"><span :style="r.unread ? 'color:var(--danger);font-weight:700' : ''">{{ r.unread }}</span></td>
              </tr>
              <tr v-if="!digestRows.length"><td colspan="3" class="emptyrow">{{ $t('notify.digest.empty') }}</td></tr>
            </tbody>
          </table>
          <div style="font-size:11px;color:var(--text-3);margin-top:10px">{{ $t('notify.digest.aiHint') }}</div>
        </div>
      </div>

      <!-- B28：订阅偏好——按分类静音；法定时限红线不可退订 -->
      <div v-show="tab === 'pref'" class="card">
        <div class="ch"><h3>通知订阅偏好</h3><span class="sub">静音的分类不再进入你的通知列表；法定时限红线始终送达</span></div>
        <div class="cb">
          <div v-for="c in PREF_CATEGORIES" :key="c.key" class="pref-row">
            <div>
              <b>{{ c.label }}</b>
              <span class="pref-hint">{{ c.hint }}</span>
            </div>
            <label class="switch" :class="{ locked: c.urgent }">
              <input type="checkbox" :checked="c.urgent || !mutedSet.has(c.key)" :disabled="c.urgent" @change="toggleMute(c.key, $event.target.checked)" />
              <span>{{ c.urgent ? '强制订阅' : (mutedSet.has(c.key) ? '已静音' : '订阅中') }}</span>
            </label>
          </div>
          <p v-if="prefSaved" class="pref-saved">已保存 ✓</p>
        </div>
      </div>

      <!-- 新建配置弹窗（按当前 tab 的 kind 显示字段）-->
      <div v-if="showAdd" class="modal-mask" @click.self="showAdd = false">
        <div class="modal-card">
          <h3>新建{{ KIND_LABEL[tab] }}</h3>
          <label class="fld">名称<input v-model="f.name" :placeholder="tab==='scenario' ? '如 外审计划临近' : (tab==='rule' ? '如 整改逾期升级' : '如 外审通知群机器人')" /></label>

          <template v-if="tab === 'scenario'">
            <label class="fld">触发条件<input v-model="f.trigger" placeholder="如 计划开始前 N 天" /></label>
            <label class="fld">接收角色/层级<input v-model="f.receiver" placeholder="如 外审责任单位 + 企微机器人" /></label>
            <label class="fld">内容要点<input v-model="f.contentPoints" placeholder="如 任务·机构·剩余天数·跳转" /></label>
            <label class="fld">通道<select v-model="f.channel"><option value="WECOM">企微</option><option value="EMAIL">邮件</option><option value="SMS">短信</option></select></label>
          </template>
          <template v-else-if="tab === 'rule'">
            <label class="fld">数据源（监控对象）
              <select v-model="f.source"><option v-for="(s, k) in SRC" :key="k" :value="k">{{ s.label }} — {{ s.desc }}</option></select>
            </label>
            <label v-if="SRC[f.source] && SRC[f.source].needDays" class="fld">{{ SRC[f.source].condLabel }}
              <input v-model.number="f.days" type="number" min="1" />
            </label>
            <label class="fld">通道<select v-model="f.channel"><option value="INBOX">站内通知</option><option value="EMAIL">邮件</option><option value="WECOM">企微</option></select></label>
            <label class="fld">内容模板（变量用花括号引用）
              <textarea v-model="f.template" rows="3" style="display:block;width:100%;margin-top:5px;padding:8px 11px;border:1px solid var(--surface-border);border-radius:var(--radius-md);background:var(--bg);color:var(--text-1);font-size:13px;font-family:inherit;line-height:1.6;outline:none;box-sizing:border-box"></textarea>
            </label>
            <div style="font-size:11px;color:var(--text-3);margin:-6px 0 12px">可用变量：{{ SRC[f.source] ? SRC[f.source].vars : '' }}</div>
          </template>
          <template v-else>
            <label class="fld">类型<select v-model="f.type"><option value="EMAIL">邮件</option><option value="SMS">短信</option><option value="WECOM">企微</option></select></label>
            <label class="fld">目标/机器人<input v-model="f.target" placeholder="邮箱 / 短信网关 / 企微机器人 webhook" /></label>
          </template>

          <label class="fld">所属组织<select v-model.number="f.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="addErr" class="cerr">{{ addErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAdd = false">取消</button>
            <button class="btn" :disabled="!f.name || addSaving" @click="submitAdd">{{ addSaving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()
import { canWrite } from '@/auth.js'

const KIND_LABEL = { scenario: '通知场景', rule: '通知规则', channel: '通道' }
const CH_LABEL = { EMAIL: '邮件', SMS: '短信', WECOM: '企微', INBOX: '站内' }
const KIND_API = { scenario: 'SCENARIO', rule: 'RULE', channel: 'CHANNEL' }

// 六轮 #7：规则引擎支持的数据源（与后端 NotifyRuleEngine 对齐）
const SRC = {
  REMEDIATION_OVERDUE: { label: '整改逾期', desc: '整改单过期未提交', needDays: false, condLabel: '', vars: '{标题} {逾期天数} {责任人}',
    tpl: '整改单「{标题}」已逾期 {逾期天数} 天（责任人：{责任人}），请尽快处理。' },
  ASSESSMENT_STALLED: { label: '评估复核滞留', desc: '评估待复核超时', needDays: true, condLabel: '滞留超过（天）', vars: '{标题} {滞留天数}',
    tpl: '风险评估「{标题}」待复核已滞留 {滞留天数} 天，请复核人尽快处理。' },
  REG_NEW: { label: '法规新采集', desc: '追踪源新采集条目', needDays: true, condLabel: '采集窗口（近 N 天）', vars: '{标题} {发布机构}',
    tpl: '追踪源新采集法规「{标题}」（{发布机构}），请评估适用性。' },
  KRI_BREACH: { label: 'KRI 触阈', desc: '指标触及预警/严重阈值', needDays: false, condLabel: '', vars: '{指标} {数值} {单位} {级别}',
    tpl: '关键风险指标「{指标}」最新值 {数值}{单位} 触及【{级别}】阈值，请核查。' }
}
const condText = (c) => {
  const dd = d(c)
  if (!SRC[dd.source]) return '—'
  if (dd.source === 'ASSESSMENT_STALLED') return '滞留 > ' + (dd.days || 0) + ' 天'
  if (dd.source === 'REG_NEW') return '近 ' + (dd.days || 1) + ' 天采集'
  return '命中即报'
}

const tab = ref('scenario')
const scenarios = ref([])
const rules = ref([])
const channels = ref([])
const items = ref([])
const loadError = ref('')

const d = (c) => { try { return JSON.parse(c.detail || '{}') } catch (e) { return {} } }
const statusCell = (c) => (c.enabled ? '启用' : '停用')

async function loadKind(kind, target) {
  try { target.value = await api.get('/notify/configs?kind=' + KIND_API[kind]) } catch (e) { target.value = [] }
}
async function loadAll() {
  loadKind('scenario', scenarios); loadKind('rule', rules); loadKind('channel', channels)
}
async function loadLog() {
  loadError.value = ''
  try { items.value = await api.get('/workbench/notifications') } catch (e) { loadError.value = e.message; items.value = [] }
}
function fmtTime(ms) { return ms ? new Date(ms).toLocaleString() : '—' }

// ===== 回执 + 定期简报（V41）=====
async function ack(n) {
  try { await api.post('/workbench/notifications/' + n.id + '/ack', {}); await loadLog() } catch (e) { /* 忽略 */ }
}
const digestDays = ref(7)
const digestRows = ref([])
async function loadDigest() {
  try { digestRows.value = await api.get('/workbench/digest?days=' + digestDays.value) } catch (e) { digestRows.value = [] }
}

// ===== B28：通知订阅偏好 =====
const PREF_CATEGORIES = [
  { key: 'REMEDIATION', label: '整改提醒', hint: '整改单逾期未提交' },
  { key: 'ASSESSMENT', label: '评估提醒', hint: '评估复核滞留、范围资产变更' },
  { key: 'REGULATION', label: '法规提醒', hint: '新采集法规摘要' },
  { key: 'RISK', label: '风险提醒', hint: 'KRI 触阈预警' },
  { key: 'AUDIT', label: '审计提醒', hint: '外部审计计划临近' },
  { key: 'FILING', label: '报送提醒', hint: '周期报送生成' },
  { key: 'URGENT', label: '法定时限红线', hint: '报送/重大事件/等保测评到期——不可退订', urgent: true }
]
const mutedSet = ref(new Set())
const prefSaved = ref(false)
async function loadPref() {
  try { mutedSet.value = new Set(await api.get('/workbench/notify-preference')) } catch (e) { mutedSet.value = new Set() }
}
async function toggleMute(key, subscribed) {
  // subscribed=false → 加入静音；true → 移出静音
  const s = new Set(mutedSet.value)
  if (subscribed) s.delete(key); else s.add(key)
  mutedSet.value = s
  try {
    await api.post('/workbench/notify-preference', { mutedCategories: [...s] })
    prefSaved.value = true; setTimeout(() => (prefSaved.value = false), 2000)
  } catch (e) { window.alert(e.message); loadPref() }
}

// 启停 / 删除
async function toggle(c) {
  try { await api.put('/notify/configs/' + c.id + '/enabled?enabled=' + (!c.enabled)); await loadAll() } catch (e) { /* 忽略 */ }
}
async function del(c) {
  if (!window.confirm(`确认删除「${c.name}」？`)) return
  try { await api.del('/notify/configs/' + c.id); await loadAll() } catch (e) { /* 忽略 */ }
}

// 新建
const showAdd = ref(false)
const addSaving = ref(false)
const addErr = ref('')
const f = reactive({ name: '', orgId: 12, trigger: '', receiver: '', contentPoints: '', channel: 'WECOM', type: 'EMAIL', target: '', source: 'REMEDIATION_OVERDUE', days: 3, template: SRC.REMEDIATION_OVERDUE.tpl })
function openAdd() {
  Object.assign(f, { name: '', orgId: 12, trigger: '', receiver: '', contentPoints: '', channel: tab.value === 'rule' ? 'INBOX' : 'WECOM', type: 'EMAIL', target: '', source: 'REMEDIATION_OVERDUE', days: 3, template: SRC.REMEDIATION_OVERDUE.tpl })
  addErr.value = ''; showAdd.value = true
}
// 切换数据源时带出该源的默认模板（用户可再改）
watch(() => f.source, (s) => { if (SRC[s]) f.template = SRC[s].tpl })
function buildDetail() {
  if (tab.value === 'scenario') return JSON.stringify({ trigger: f.trigger, receiver: f.receiver, contentPoints: f.contentPoints, channel: f.channel })
  if (tab.value === 'rule') return JSON.stringify({ source: f.source, days: f.days || 0, channel: f.channel, template: f.template })
  return JSON.stringify({ type: f.type, target: f.target })
}

// ===== 六轮 #7：手动触发一轮规则评估（平时由内核每 15 分钟自动跑）=====
const engineBusy = ref(false)
const engineMsg = ref('')
async function runEngine() {
  engineBusy.value = true; engineMsg.value = ''
  try {
    const r = await api.post('/notify/configs/run-engine', {})
    engineMsg.value = '本轮新产 ' + r.produced + ' 条告警' + (r.produced ? '（见提醒记录）' : '（无新命中或均已告警过）')
      + (r.pushed ? '，企微外推成功 ' + r.pushed + ' 条' : '')
    loadSendLogs()
    await loadLog()
    setTimeout(() => (engineMsg.value = ''), 5000)
  } catch (e) { engineMsg.value = e.message } finally { engineBusy.value = false }
}
async function submitAdd() {
  addSaving.value = true; addErr.value = ''
  try {
    await api.post('/notify/configs', { orgId: f.orgId, kind: KIND_API[tab.value], name: f.name, detail: buildDetail() })
    showAdd.value = false; await loadAll()
  } catch (e) { addErr.value = e.message } finally { addSaving.value = false }
}

// ===== 八轮 8-1：发送留痕 =====
const sendLogs = ref([])
async function loadSendLogs() {
  try { sendLogs.value = await api.get('/notify/configs/send-logs') } catch (e) { sendLogs.value = [] }
}

onMounted(() => { loadAll(); loadLog(); loadSendLogs() })
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 5px 10px; font-size: 11.5px; box-shadow: none; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.tabbar { display: flex; gap: 6px; margin-bottom: 14px; border-bottom: 1px solid var(--surface-border); }
.tabbar button { border: 0; background: none; color: var(--text-2); font-size: 13px; font-weight: 600; padding: 9px 14px; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; font-family: inherit; }
.tabbar button:hover { color: var(--text-1); }
.tabbar button.on { color: var(--accent-strong); border-bottom-color: var(--accent-strong); }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.ch .sub { font-size: 11px; color: var(--text-3); margin-left: auto; }
.cb { padding: 14px 18px 18px; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 12px 10px; }
tbody td { padding: 9px 12px; border-top: 1px solid var(--border-subtle); font-size: 12px; vertical-align: top; }
.num { font-variant-numeric: tabular-nums; white-space: nowrap; }
.code { font-weight: 600; color: var(--accent-strong); }
.muted { color: var(--text-2); max-width: 220px; }
.evt { font-family: var(--font-mono, monospace); font-size: 11px; background: var(--warning-tint); color: #a87d22; padding: 1px 7px; border-radius: 4px; font-weight: 600; }
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: var(--info-tint); color: var(--info); }
.tag { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: rgba(120,120,120,.12); color: var(--text-2); }
.tag.h { background: var(--danger-tint, rgba(180,35,45,.1)); color: var(--danger); }
.ops { white-space: nowrap; }
.mini { padding: 3px 9px; font-size: 11px; border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-2); border-radius: 6px; cursor: pointer; margin-right: 4px; }
.mini:hover { background: var(--accent-tint); }
.mini.danger:hover { color: var(--danger); border-color: var(--danger); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 440px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; box-sizing: border-box; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0 0 12px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
/* V41 回执/降噪/简报 */
.merge { display: inline-block; margin-left: 6px; font-size: 10px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 7px; }
.ackd { font-size: 11px; color: var(--success); font-weight: 600; }
.sel { height: 30px; padding: 0 8px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12px; font-family: inherit; outline: none; }
.ch .sub { font-size: 10.5px; color: var(--text-3); }
/* B28 订阅偏好 */
.pref-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 2px; border-bottom: 1px solid var(--border-subtle); }
.pref-row:last-of-type { border-bottom: 0; }
.pref-row b { font-size: 13px; color: var(--text-1); }
.pref-hint { display: block; font-size: 11px; color: var(--text-3); margin-top: 2px; }
.switch { display: inline-flex; align-items: center; gap: 7px; font-size: 12px; color: var(--text-2); cursor: pointer; }
.switch.locked { color: var(--text-3); cursor: not-allowed; }
.switch input { width: 16px; height: 16px; accent-color: var(--accent); }
.pref-saved { color: var(--success); font-size: 12px; font-weight: 600; margin-top: 10px; }
</style>
