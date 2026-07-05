<template>
  <!--
    我的待办（MyTasksView）：① 我的审批（按登录人角色匹配的待办审批，真·按登录人）② 组织范围待办（跨模块归并）。
    功能真源 = 后端 /api/workbench/my-approvals + /todos；视觉遵 tokens.css。
  -->
  <AppShell>
    <section class="view view-wb">
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('todo.tag') }}</div>
          <h1>{{ $t('todo.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn ghost" @click="load">{{ $t('todo.refresh') }}</button>
      </div>

      <!-- ① 我的审批（真·按登录人：登录人角色匹配候选组的待办审批） -->
      <div class="card" style="margin-bottom: 14px">
        <div class="ch"><h3>{{ $t('todo.myApprovals') }}</h3><span class="cnt mine">{{ myApprovals.length }}</span></div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 680px">
            <thead><tr>
              <th>{{ $t('todo.ath.biz') }}</th><th>{{ $t('todo.ath.node') }}</th>
              <th>{{ $t('todo.ath.role') }}</th><th>{{ $t('todo.ath.time') }}</th><th>操作</th>
            </tr></thead>
            <tbody>
              <tr v-for="(a, i) in myApprovals" :key="i">
                <td><span class="tpill APPROVAL">{{ BIZ_TXT[a.bizType] || a.bizType }}</span> <span class="code">#{{ a.bizId }}</span></td>
                <td>{{ a.nodeName }}</td>
                <td><span class="rolepill">{{ a.roleGroup }}</span></td>
                <td class="num">{{ fmtTime(a.createdMs) }}</td>
                <!-- 八轮 8-7（C2）：审批在待办处置——带意见 通过/驳回（制度类先行，其余类型给入口指引） -->
                <td class="ops">
                  <template v-if="a.bizType === 'POLICY'">
                    <button class="mini" style="color:var(--success);border-color:var(--success)" @click="decidePolicy(a, true)">通过</button>
                    <button class="mini danger" @click="decidePolicy(a, false)">驳回</button>
                  </template>
                  <span v-else class="muted" style="font-size:11px">到对应模块页处置</span>
                </td>
              </tr>
              <tr v-if="!myApprovals.length">
                <td colspan="5" class="emptyrow">{{ $t('todo.myEmpty') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ②③④ 四类分组：待填写 / 待签署 / 待整改（需求 D1-7§5.11 分组聚合 + 时限预警）-->
      <div class="g3">
        <!-- 待填写：进行中的评估 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('todo.grp.fill') }}</h3><span class="cnt">{{ toFill.length }}</span><span class="sub">{{ $t('todo.grp.fillSub') }}</span></div>
          <div class="cb" style="padding-top:0">
            <div v-for="a in toFill" :key="a.id" class="td-row clk" @click="go('/risk')">
              <span class="code">#{{ a.id }}</span><span class="td-t">{{ a.title }}</span>
              <span class="st doing" style="margin-left:auto"><span class="d"></span>{{ a.status === 'DRAFT' ? $t('todo.grp.draft') : $t('todo.grp.filling') }}</span>
            </div>
            <div v-if="!toFill.length" class="emptyrow">{{ $t('todo.grp.fillEmpty') }}</div>
          </div>
        </div>
        <!-- 待签署：已生效制度 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('todo.grp.sign') }}</h3><span class="cnt">{{ toSign.length }}</span><span class="sub">{{ $t('todo.grp.signSub') }}</span></div>
          <div class="cb" style="padding-top:0">
            <div v-for="p in toSign" :key="p.id" class="td-row clk" @click="go('/policy')">
              <span class="code">{{ p.code }}</span><span class="td-t">{{ p.title }}</span>
              <span class="pill" style="margin-left:auto">v{{ p.version }}</span>
            </div>
            <div v-if="!toSign.length" class="emptyrow">{{ $t('todo.grp.signEmpty') }}</div>
          </div>
        </div>
        <!-- 待整改：未闭环整改单 + 时限预警 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('todo.grp.remed') }}</h3><span class="cnt">{{ toRemed.length }}</span><span class="sub">{{ $t('todo.grp.remedSub') }}</span></div>
          <div class="cb" style="padding-top:0">
            <div v-for="r in toRemed" :key="r.id" class="td-row clk" @click="go(r._risk ? '/risk' : '/internal-audit')">
              <span class="code">{{ r._risk ? 'RF-' + String(r.id).slice(1) : 'RO-' + r.id }}</span><span class="td-t">{{ r.measure || r.assignee || $t('todo.grp.remedDefault') }}</span>
              <span class="duetag" :class="dueCls(r.dueDate)" style="margin-left:auto">{{ dueText(r.dueDate) }}</span>
            </div>
            <div v-if="!toRemed.length" class="emptyrow">{{ $t('todo.grp.remedEmpty') }}</div>
          </div>
        </div>
      </div>

      <!-- ⑤ 组织范围待办 -->
      <div class="card">
        <div class="ch"><h3>{{ $t('todo.listTitle') }}</h3><span class="cnt">{{ todos.length }}</span></div>
        <div class="cb" style="overflow-x: auto; padding-top: 0">
          <table style="min-width: 720px">
            <thead>
              <tr>
                <th>{{ $t('todo.th.type') }}</th>
                <th>{{ $t('todo.th.matter') }}</th>
                <th>{{ $t('todo.th.due') }}</th>
                <th>{{ $t('todo.th.status') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(t, i) in todos" :key="i">
                <td><span class="tpill" :class="t.type">{{ $t('todo.type.' + t.type) }}</span></td>
                <td><span class="code">#{{ t.refId }}</span> {{ t.title }}</td>
                <td class="num">{{ t.dueDate || '—' }}</td>
                <td><span class="st"><span class="d"></span>{{ t.status }}</span></td>
              </tr>
              <tr v-if="!todos.length">
                <td colspan="4" class="emptyrow">{{ loadError || $t('todo.empty') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'

const { t } = useI18n()

const todos = ref([])
const myApprovals = ref([])

// ===== 八轮 8-7（C2）：待办内处置审批（制度类先行；意见必填驳回、可选通过）=====
const BIZ_TXT = { POLICY: '制度审批', RISK_ACCEPTANCE: '风险接受', FEEDBACK_OUTBOUND: '出站审批', REG_FILING: '报送复核' }
async function decidePolicy(a, approved) {
  const tip = approved ? '审批意见（可空）：' : '驳回原因（必填）：'
  const comment = window.prompt(`制度 #${a.bizId} ${approved ? '审批通过' : '驳回退回'}。${tip}`, '')
  if (comment === null) return
  if (!approved && !comment.trim()) { window.alert('驳回必须填写原因'); return }
  try {
    if (approved) await api.post('/policies/' + a.bizId + '/approve', {})
    else await api.post('/policies/' + a.bizId + '/reject', { reason: comment })
    await load()
  } catch (e) { window.alert(e.message) }
}
const loadError = ref('')
function fmtTime(ms) {
  if (!ms) return '—'
  const d = new Date(ms)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
// ===== 四类分组：待填写 / 待签署 / 待整改（+ 已有的待审批）=====
const toFill = ref([])
const toSign = ref([])
const toRemed = ref([])
function go(path) { window.location.hash = '#' + path }
// 时限预警：逾期红 / 7 日内琥珀 / 其余灰
function dueCls(d) {
  if (!d) return ''
  const days = Math.ceil((new Date(d) - new Date()) / 86400000)
  return days < 0 ? 'over' : (days <= 7 ? 'warn' : '')
}
function dueText(d) {
  if (!d) return t('todo.due.none')
  const days = Math.ceil((new Date(d) - new Date()) / 86400000)
  return days < 0 ? t('todo.due.overdue', { n: -days }) : (days === 0 ? t('todo.due.today') : t('todo.due.left', { n: days }))
}
async function loadGroups() {
  try {
    const [assessments, policies, remIn, remEx, findings] = await Promise.all([
      api.get('/assessments'), api.get('/policies'),
      api.get('/remediation-orders?type=INTERNAL').catch(() => []),
      api.get('/remediation-orders?type=EXTERNAL').catch(() => []),
      api.get('/risk-findings/register').catch(() => [])   // M2 深度包 C12：处置中的风险发现也属"待整改"
    ])
    toFill.value = assessments.filter((a) => a.status === 'DRAFT' || a.status === 'IN_PROGRESS')
    toSign.value = policies.filter((p) => p.status === 'EFFECTIVE')
    // C12：整改单 + 处置中风险发现合并展示（风险发现无到期日，靠 measure 文案与 RF- 前缀区分，点击跳风险登记册）
    const riskRows = findings.filter((f) => f.status === 'IN_TREATMENT')
      .map((f) => ({ id: 'F' + f.id, measure: '【风险处置】' + f.title, dueDate: null, _risk: true }))
    toRemed.value = [...remIn, ...remEx].filter((r) => r.status === 'PENDING' || r.status === 'IN_PROGRESS')
      .sort((a, b) => (a.dueDate || '9999') < (b.dueDate || '9999') ? -1 : 1)
      .concat(riskRows)
  } catch (e) { /* 各组独立容错 */ }
}

async function load() {
  loadError.value = ''
  try {
    todos.value = await api.get('/workbench/todos')
  } catch (e) {
    loadError.value = e.message
    todos.value = []
  }
  try {
    myApprovals.value = await api.get('/workbench/my-approvals')
  } catch (e) {
    myApprovals.value = []
  }
  loadGroups()
}
onMounted(load)
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; letter-spacing: -0.3px; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.cb { padding: 14px 18px 18px; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 14px 10px; }
tbody td { padding: 10px 14px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
.num { font-variant-numeric: tabular-nums; }
.code { font-weight: 700; color: var(--accent-strong); }
.tpill { display: inline-block; padding: 2px 9px; border-radius: 6px; font-size: 10.5px; font-weight: 700; background: rgba(120,120,120,0.1); color: var(--text-2); }
.tpill.REMEDIATION { background: var(--danger-tint); color: var(--danger); }
.tpill.COMPLIANCE_ITEM { background: var(--info-tint); color: var(--info); }
.tpill.REG_FILING { background: var(--warning-tint); color: #a87d22; }
.tpill.APPROVAL { background: var(--accent-weak); color: var(--accent-strong); }
.cnt.mine { color: #fff; background: var(--accent); }
.rolepill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: rgba(120,120,120,0.1); color: var(--text-2); font-family: var(--font-mono, monospace); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }

/* ===== 四类分组卡片（待填写/待签署/待整改） ===== */
.g3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 14px; }
@media (max-width: 1100px) { .g3 { grid-template-columns: 1fr; } }
.ch .sub { font-size: 10.5px; color: var(--text-3); margin-left: auto; }
.td-row { display: flex; align-items: center; gap: 8px; padding: 9px 2px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
.td-row:first-child { border-top: none; }
.td-row.clk { cursor: pointer; }
.td-row.clk:hover { background: var(--accent-weak); }
.td-t { color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 60%; }
.st.doing .d { background: var(--info); }
.pill { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 10.5px; font-weight: 700; background: rgba(120,120,120,0.1); color: var(--text-2); }
/* 时限预警：逾期红 / 7 日内琥珀 / 其余灰 */
.duetag { display: inline-block; padding: 2px 9px; border-radius: 6px; font-size: 10.5px; font-weight: 700; background: rgba(120,120,120,0.1); color: var(--text-2); white-space: nowrap; }
.duetag.warn { background: var(--warning-tint); color: #a87d22; }
.duetag.over { background: var(--danger-tint); color: var(--danger); }
/* 八轮 8-7：待办内审批操作 */
.ops { white-space: nowrap; }
.mini { padding: 3px 9px; font-size: 11px; border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-2); border-radius: 6px; cursor: pointer; margin-right: 4px; }
.mini:hover { background: var(--accent-weak); }
.mini.danger:hover { color: var(--danger); border-color: var(--danger); }
</style>
