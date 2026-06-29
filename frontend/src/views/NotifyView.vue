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

      <!-- 通知规则 -->
      <div v-show="tab === 'rule'" class="card">
        <div class="ch"><h3>通知规则</h3><span class="cnt">{{ rules.length }}</span><span class="sub">触发事件 → 级别 → 通道</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:620px">
            <thead><tr><th>规则</th><th>触发事件</th><th>级别</th><th>通道</th><th>状态</th><th></th></tr></thead>
            <tbody>
              <tr v-for="c in rules" :key="c.id">
                <td><b>{{ c.name }}</b></td>
                <td class="muted">{{ d(c).triggerEvent || '—' }}</td>
                <td><span class="tag" :class="d(c).level === 'URGENT' ? 'h' : ''">{{ LV_LABEL[d(c).level] || '普通' }}</span></td>
                <td><span class="pill">{{ CH_LABEL[d(c).channel] || d(c).channel || '—' }}</span></td>
                <td>{{ statusCell(c) }}</td>
                <td class="ops"><template v-if="canWrite('notify')">
                  <button class="mini" @click="toggle(c)">{{ c.enabled ? '停用' : '启用' }}</button>
                  <button class="mini danger" @click="del(c)">删</button>
                </template></td>
              </tr>
              <tr v-if="!rules.length"><td colspan="6" class="emptyrow">暂无通知规则，点「＋ 新建通知规则」。</td></tr>
            </tbody>
          </table>
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
        </div>
      </div>

      <!-- 提醒记录（调度内核派发）-->
      <div v-show="tab === 'log'" class="card">
        <div class="ch"><h3>{{ $t('notify.listTitle') }}</h3><span class="cnt">{{ items.length }}</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:720px">
            <thead><tr><th>{{ $t('notify.th.time') }}</th><th>{{ $t('notify.th.event') }}</th><th>{{ $t('notify.th.object') }}</th><th>{{ $t('notify.th.threshold') }}</th></tr></thead>
            <tbody>
              <tr v-for="(n, i) in items" :key="i">
                <td class="num">{{ fmtTime(n.createdAtMs) }}</td>
                <td><span class="evt">{{ n.eventType }}</span></td>
                <td class="code">{{ n.objectType }}:{{ n.objectId }}</td>
                <td class="num">{{ n.thresholdKey }}</td>
              </tr>
              <tr v-if="!items.length"><td colspan="4" class="emptyrow">{{ loadError || $t('notify.empty') }}</td></tr>
            </tbody>
          </table>
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
            <label class="fld">触发事件<input v-model="f.triggerEvent" placeholder="如 整改任务超期未闭环" /></label>
            <label class="fld">级别<select v-model="f.level"><option value="NORMAL">普通</option><option value="URGENT">紧急</option></select></label>
            <label class="fld">通道<select v-model="f.channel"><option value="EMAIL">邮件</option><option value="SMS">短信</option><option value="WECOM">企微</option></select></label>
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
import { ref, reactive, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()
import { canWrite } from '@/auth.js'

const KIND_LABEL = { scenario: '通知场景', rule: '通知规则', channel: '通道' }
const CH_LABEL = { EMAIL: '邮件', SMS: '短信', WECOM: '企微' }
const LV_LABEL = { NORMAL: '普通', URGENT: '紧急' }
const KIND_API = { scenario: 'SCENARIO', rule: 'RULE', channel: 'CHANNEL' }

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
const f = reactive({ name: '', orgId: 12, trigger: '', receiver: '', contentPoints: '', channel: 'WECOM', triggerEvent: '', level: 'NORMAL', type: 'EMAIL', target: '' })
function openAdd() {
  Object.assign(f, { name: '', orgId: 12, trigger: '', receiver: '', contentPoints: '', channel: tab.value === 'rule' ? 'EMAIL' : 'WECOM', triggerEvent: '', level: 'NORMAL', type: 'EMAIL', target: '' })
  addErr.value = ''; showAdd.value = true
}
function buildDetail() {
  if (tab.value === 'scenario') return JSON.stringify({ trigger: f.trigger, receiver: f.receiver, contentPoints: f.contentPoints, channel: f.channel })
  if (tab.value === 'rule') return JSON.stringify({ triggerEvent: f.triggerEvent, level: f.level, channel: f.channel })
  return JSON.stringify({ type: f.type, target: f.target })
}
async function submitAdd() {
  addSaving.value = true; addErr.value = ''
  try {
    await api.post('/notify/configs', { orgId: f.orgId, kind: KIND_API[tab.value], name: f.name, detail: buildDetail() })
    showAdd.value = false; await loadAll()
  } catch (e) { addErr.value = e.message } finally { addSaving.value = false }
}

onMounted(() => { loadAll(); loadLog() })
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
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
</style>
