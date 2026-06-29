<template>
  <!--
    内部审计（InternalAuditView · M3）：审计计划 → 审计发现 → 整改跟踪。
    功能真源 = 后端 audit/management（/api/audit-plans?type=INTERNAL、/api/audit-findings、/api/remediation-orders）。
    与外部审计共用审计管理后端，按 auditType=INTERNAL 分视图；写门控 "extaudit"（后端审计管理写权限）。
    检查表执行（表单引擎接入）后置——见底部说明。
  -->
  <AppShell>
    <section class="view view-ia">
      <div class="phead">
        <div><div class="kqt">M3 · 内部审计</div><h1>内部审计实施与管理</h1></div>
        <div class="sp"></div>
        <button v-if="tab === 'plan'" class="btn" :disabled="!canWrite('extaudit')"
                :title="canWrite('extaudit') ? '' : '无审计管理写权限'" @click="openPlan">＋ 新建审计计划</button>
      </div>

      <!-- KPI -->
      <div class="kpibar">
        <div class="kc"><div class="l">内审计划</div><div class="v">{{ plans.length }}</div></div>
        <div class="kc"><div class="l">实施中</div><div class="v" style="color:var(--accent-strong)">{{ inProgress }}</div></div>
        <div class="kc"><div class="l">审计发现</div><div class="v">{{ findings.length }}</div></div>
        <div class="kc"><div class="l">高风险发现</div><div class="v" style="color:var(--danger)">{{ highFindings }}</div></div>
      </div>

      <div class="tabbar">
        <button :class="{ on: tab === 'plan' }" @click="tab = 'plan'">审计计划</button>
        <button :class="{ on: tab === 'finding' }" @click="tab = 'finding'">审计发现</button>
        <button :class="{ on: tab === 'remed' }" @click="tab = 'remed'">整改跟踪</button>
      </div>

      <!-- 审计计划 -->
      <div v-show="tab === 'plan'" class="card">
        <div class="ch"><h3>审计计划</h3><span class="cnt">{{ plans.length }}</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:720px">
            <thead><tr><th>编号</th><th>主题</th><th>类型</th><th>开始日</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="p in plans" :key="p.id" class="clk" :class="{ on: p.id === planId }" @click="pickPlan(p)">
                <td class="code">AP-{{ p.id }}</td>
                <td><b>{{ p.title }}</b></td>
                <td><span class="pill">内部审计</span></td>
                <td class="num">{{ p.planStartDate || '—' }}</td>
                <td><span class="st" :class="PLAN_CLS[p.status]"><span class="d"></span>{{ PLAN_LABEL[p.status] }}</span></td>
                <td class="ops" @click.stop>
                  <template v-if="canWrite('extaudit')">
                    <button v-if="p.status==='PLANNED'" class="mini" @click="planAction(p,'start')">开始</button>
                    <button v-if="p.status==='IN_PROGRESS'" class="mini" @click="planAction(p,'report')">出具报告</button>
                    <button v-if="p.status==='REPORTING'" class="mini" @click="planAction(p,'close')">关闭</button>
                    <button v-if="p.status==='PLANNED'||p.status==='IN_PROGRESS'" class="mini danger" @click="planAction(p,'cancel')">取消</button>
                  </template>
                </td>
              </tr>
              <tr v-if="!plans.length"><td colspan="6" class="emptyrow">暂无内审计划，点「＋ 新建审计计划」。</td></tr>
            </tbody>
          </table>
          <p v-if="opMsg" class="ok-msg">{{ opMsg }}</p>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
        </div>
      </div>

      <!-- 审计发现 -->
      <div v-show="tab === 'finding'" class="card">
        <div class="ch">
          <h3>审计发现</h3>
          <select class="sel" v-model.number="planId" @change="loadFindings">
            <option :value="0" disabled>— 选择审计计划 —</option>
            <option v-for="p in plans" :key="p.id" :value="p.id">AP-{{ p.id }} · {{ p.title }}</option>
          </select>
          <button v-if="planId && canWrite('extaudit')" class="btn sm" style="margin-left:auto" @click="openFinding">＋ 新建发现</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <div v-if="!planId" class="hint">先选择一个审计计划。</div>
          <table v-else style="min-width:600px">
            <thead><tr><th>编号</th><th>问题</th><th>严重度</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="f in findings" :key="f.id" class="clk" :class="{ on: f.id === findingId }" @click="pickFinding(f)">
                <td class="code">AF-{{ f.id }}</td>
                <td><b>{{ f.title }}</b></td>
                <td><span class="tag" :class="SEV_CLS[f.severity]">{{ SEV_LABEL[f.severity] }}</span></td>
                <td class="ops" @click.stop>
                  <button v-if="canWrite('extaudit')" class="mini" @click="openRemed(f)">下达整改</button>
                </td>
              </tr>
              <tr v-if="!findings.length"><td colspan="4" class="emptyrow">该计划暂无审计发现。</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 整改跟踪 -->
      <div v-show="tab === 'remed'" class="card">
        <div class="ch">
          <h3>整改跟踪</h3>
          <select class="sel" v-model.number="findingId" @change="loadRemed">
            <option :value="0" disabled>— 选择审计发现 —</option>
            <option v-for="f in findings" :key="f.id" :value="f.id">AF-{{ f.id }} · {{ f.title }}</option>
          </select>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <div v-if="!findingId" class="hint">先在「审计发现」选一条发现，或在此选择。</div>
          <table v-else style="min-width:640px">
            <thead><tr><th>编号</th><th>责任人</th><th>整改措施</th><th>截止</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="r in remeds" :key="r.id">
                <td class="code">RO-{{ r.id }}</td>
                <td>{{ r.assignee || '—' }}</td>
                <td class="muted">{{ r.measure || '—' }}</td>
                <td class="num">{{ r.dueDate || '—' }}</td>
                <td><span class="st" :class="REM_CLS[r.status]"><span class="d"></span>{{ REM_LABEL[r.status] }}</span></td>
                <td class="ops">
                  <template v-if="canWrite('extaudit')">
                    <button v-if="r.status==='PENDING'" class="mini" @click="remAction(r,'start')">开始</button>
                    <button v-if="r.status==='IN_PROGRESS'" class="mini" @click="remAction(r,'submit')">提交</button>
                    <button v-if="r.status==='SUBMITTED'" class="mini" @click="remAction(r,'verify')">验证通过</button>
                    <button v-if="r.status==='SUBMITTED'" class="mini danger" @click="remAction(r,'reject')">驳回</button>
                  </template>
                </td>
              </tr>
              <tr v-if="!remeds.length"><td colspan="6" class="emptyrow">该发现暂无整改单，去「审计发现」下达整改。</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <p class="note">检查表执行（基于表单引擎的等保/ISO 审计检查表）将在后续接入，复用风险评估同一套 .docx 表单引擎。</p>

      <!-- 新建计划弹窗 -->
      <div v-if="showPlan" class="modal-mask" @click.self="showPlan = false">
        <div class="modal-card">
          <h3>新建审计计划</h3>
          <label class="fld">审计主题<input v-model="pf.title" placeholder="如 支付系统安全审计" /></label>
          <label class="fld">计划开始日<input type="date" v-model="pf.planStartDate" /></label>
          <label class="fld">所属组织<select v-model.number="pf.orgId"><option :value="12">支付科技</option><option :value="13">消费金融</option></select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showPlan = false">取消</button>
            <button class="btn" :disabled="!pf.title || saving" @click="submitPlan">{{ saving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>

      <!-- 新建发现弹窗 -->
      <div v-if="showFinding" class="modal-mask" @click.self="showFinding = false">
        <div class="modal-card">
          <h3>新建审计发现</h3>
          <label class="fld">问题描述<input v-model="ff.title" placeholder="如 访问控制策略未落实最小授权" /></label>
          <label class="fld">严重度<select v-model="ff.severity">
            <option value="VERY_LOW">极低</option><option value="LOW">低</option><option value="MID">中</option><option value="HIGH">高</option>
          </select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showFinding = false">取消</button>
            <button class="btn" :disabled="!ff.title || saving" @click="submitFinding">{{ saving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>

      <!-- 下达整改弹窗 -->
      <div v-if="showRemed" class="modal-mask" @click.self="showRemed = false">
        <div class="modal-card">
          <h3>下达整改单</h3>
          <p class="muted" style="margin:-6px 0 12px">针对发现 AF-{{ remedTarget && remedTarget.id }}：{{ remedTarget && remedTarget.title }}</p>
          <label class="fld">责任人<input v-model="rf.assignee" placeholder="如 张三" /></label>
          <label class="fld">整改措施<input v-model="rf.measure" placeholder="如 配置最小授权策略并复核" /></label>
          <label class="fld">截止日期<input type="date" v-model="rf.dueDate" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showRemed = false">取消</button>
            <button class="btn" :disabled="!rf.assignee || saving" @click="submitRemed">{{ saving ? '提交中…' : '确认' }}</button>
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
import { canWrite } from '@/auth.js'

const PLAN_LABEL = { PLANNED: '已计划', IN_PROGRESS: '实施中', REPORTING: '待签批', CLOSED: '已关闭', CANCELLED: '已取消' }
const PLAN_CLS = { PLANNED: 'wait', IN_PROGRESS: 'doing', REPORTING: 'wait', CLOSED: 'ok', CANCELLED: 'over' }
const SEV_LABEL = { VERY_LOW: '极低', LOW: '低', MID: '中', HIGH: '高' }
const SEV_CLS = { VERY_LOW: '', LOW: '', MID: 'm', HIGH: 'h' }
const REM_LABEL = { PENDING: '待开始', IN_PROGRESS: '整改中', SUBMITTED: '已提交', VERIFIED: '已验证', REJECTED: '已驳回' }
const REM_CLS = { PENDING: 'wait', IN_PROGRESS: 'doing', SUBMITTED: 'wait', VERIFIED: 'ok', REJECTED: 'over' }

const tab = ref('plan')
const plans = ref([])
const findings = ref([])
const remeds = ref([])
const planId = ref(0)
const findingId = ref(0)
const opMsg = ref('')
const opErr = ref('')
const saving = ref(false)

const inProgress = computed(() => plans.value.filter((p) => p.status === 'IN_PROGRESS').length)
const highFindings = computed(() => findings.value.filter((f) => f.severity === 'HIGH').length)

async function loadPlans() {
  try { plans.value = await api.get('/audit-plans?type=INTERNAL') } catch (e) { plans.value = [] }
}
async function loadFindings() {
  if (!planId.value) { findings.value = []; return }
  try { findings.value = await api.get('/audit-findings?auditPlanId=' + planId.value) } catch (e) { findings.value = [] }
}
async function loadRemed() {
  if (!findingId.value) { remeds.value = []; return }
  try { remeds.value = await api.get('/remediation-orders?findingId=' + findingId.value) } catch (e) { remeds.value = [] }
}
function pickPlan(p) { planId.value = p.id; tab.value = 'finding'; loadFindings() }
function pickFinding(f) { findingId.value = f.id; tab.value = 'remed'; loadRemed() }

async function planAction(p, action) {
  opMsg.value = ''; opErr.value = ''
  try { await api.post('/audit-plans/' + p.id + '/' + action, {}); opMsg.value = '已' + ({ start: '开始', report: '出具报告', close: '关闭', cancel: '取消' }[action]); await loadPlans(); setTimeout(() => (opMsg.value = ''), 2000) }
  catch (e) { opErr.value = e.message }
}
async function remAction(r, action) {
  opErr.value = ''
  try { await api.post('/remediation-orders/' + r.id + '/' + action, {}); await loadRemed() } catch (e) { opErr.value = e.message }
}

// 新建计划
const showPlan = ref(false)
const pf = reactive({ title: '', planStartDate: '', orgId: 12 })
function openPlan() { Object.assign(pf, { title: '', planStartDate: '', orgId: 12 }); opErr.value = ''; showPlan.value = true }
async function submitPlan() {
  saving.value = true; opErr.value = ''
  try {
    await api.post('/audit-plans', { orgId: pf.orgId, title: pf.title, auditType: 'INTERNAL', planStartDate: pf.planStartDate || null })
    showPlan.value = false; await loadPlans()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}

// 新建发现
const showFinding = ref(false)
const ff = reactive({ title: '', severity: 'MID' })
function openFinding() { Object.assign(ff, { title: '', severity: 'MID' }); opErr.value = ''; showFinding.value = true }
async function submitFinding() {
  saving.value = true; opErr.value = ''
  try {
    const plan = plans.value.find((p) => p.id === planId.value)
    await api.post('/audit-findings', { orgId: plan ? plan.orgId : 12, auditPlanId: planId.value, title: ff.title, severity: ff.severity })
    showFinding.value = false; await loadFindings()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}

// 下达整改
const showRemed = ref(false)
const remedTarget = ref(null)
const rf = reactive({ assignee: '', measure: '', dueDate: '' })
function openRemed(f) { remedTarget.value = f; findingId.value = f.id; Object.assign(rf, { assignee: '', measure: '', dueDate: '' }); opErr.value = ''; showRemed.value = true }
async function submitRemed() {
  saving.value = true; opErr.value = ''
  try {
    await api.post('/remediation-orders', { findingId: remedTarget.value.id, assignee: rf.assignee, dueDate: rf.dueDate || null, measure: rf.measure })
    showRemed.value = false; await loadRemed()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}

onMounted(loadPlans)
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 5px 11px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.kpibar { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.kpibar .kc { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); padding: 14px 16px; box-shadow: var(--shadow-1); }
.kpibar .l { font-size: 11.5px; color: var(--text-3); margin-bottom: 6px; }
.kpibar .v { font-size: 22px; font-weight: 760; font-family: var(--font-display); }
.tabbar { display: flex; gap: 6px; margin-bottom: 14px; border-bottom: 1px solid var(--surface-border); }
.tabbar button { border: 0; background: none; color: var(--text-2); font-size: 13px; font-weight: 600; padding: 9px 14px; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; font-family: inherit; }
.tabbar button:hover { color: var(--text-1); }
.tabbar button.on { color: var(--accent-strong); border-bottom-color: var(--accent-strong); }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 8px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.ch .sel { margin-left: 10px; height: 32px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12.5px; }
.cb { padding: 14px 18px 18px; }
.hint { color: var(--text-3); font-size: 12.5px; padding: 22px 8px; text-align: center; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 12px 10px; }
tbody td { padding: 9px 12px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
tbody tr.clk { cursor: pointer; }
tbody tr.clk:hover, tbody tr.on { background: var(--accent-tint); }
.num { font-variant-numeric: tabular-nums; white-space: nowrap; }
.code { font-weight: 700; color: var(--accent-strong); font-family: var(--font-mono, monospace); font-size: 11.5px; }
.muted { color: var(--text-2); max-width: 240px; }
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: var(--info-tint); color: var(--info); }
.tag { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: rgba(120,120,120,.12); color: var(--text-2); }
.tag.m { background: var(--warning-tint); color: #a87d22; }
.tag.h { background: var(--danger-tint, rgba(180,35,45,.1)); color: var(--danger); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.doing { color: var(--accent-strong); } .st.doing .d { background: var(--accent-strong); }
.st.wait { color: #a87d22; } .st.wait .d { background: #a87d22; }
.st.over { color: var(--danger); } .st.over .d { background: var(--danger); }
.ops { white-space: nowrap; }
.mini { padding: 3px 9px; font-size: 11px; border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-2); border-radius: 6px; cursor: pointer; margin-right: 4px; }
.mini:hover { background: var(--accent-tint); }
.mini.danger:hover { color: var(--danger); border-color: var(--danger); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.ok-msg { color: var(--success); font-weight: 600; font-size: 12px; margin: 10px 0 0; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 8px 0 0; }
.note { font-size: 11.5px; color: var(--text-3); margin: 14px 2px 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 440px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; box-sizing: border-box; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
</style>
