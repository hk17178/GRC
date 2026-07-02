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
              <th>{{ $t('todo.ath.role') }}</th><th>{{ $t('todo.ath.time') }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="(a, i) in myApprovals" :key="i">
                <td><span class="tpill APPROVAL">{{ a.bizType }}</span> <span class="code">#{{ a.bizId }}</span></td>
                <td>{{ a.nodeName }}</td>
                <td><span class="rolepill">{{ a.roleGroup }}</span></td>
                <td class="num">{{ fmtTime(a.createdMs) }}</td>
              </tr>
              <tr v-if="!myApprovals.length">
                <td colspan="4" class="emptyrow">{{ $t('todo.myEmpty') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ②③④ 四类分组：待填写 / 待签署 / 待整改（需求 D1-7§5.11 分组聚合 + 时限预警）-->
      <div class="g3">
        <!-- 待填写：进行中的评估 -->
        <div class="card">
          <div class="ch"><h3>待填写</h3><span class="cnt">{{ toFill.length }}</span><span class="sub">进行中评估</span></div>
          <div class="cb" style="padding-top:0">
            <div v-for="a in toFill" :key="a.id" class="td-row clk" @click="go('/risk')">
              <span class="code">#{{ a.id }}</span><span class="td-t">{{ a.title }}</span>
              <span class="st doing" style="margin-left:auto"><span class="d"></span>{{ a.status === 'DRAFT' ? '草稿' : '填写中' }}</span>
            </div>
            <div v-if="!toFill.length" class="emptyrow">无待填写评估</div>
          </div>
        </div>
        <!-- 待签署：已生效制度 -->
        <div class="card">
          <div class="ch"><h3>待签署</h3><span class="cnt">{{ toSign.length }}</span><span class="sub">生效制度签署确认</span></div>
          <div class="cb" style="padding-top:0">
            <div v-for="p in toSign" :key="p.id" class="td-row clk" @click="go('/policy')">
              <span class="code">{{ p.code }}</span><span class="td-t">{{ p.title }}</span>
              <span class="pill" style="margin-left:auto">v{{ p.version }}</span>
            </div>
            <div v-if="!toSign.length" class="emptyrow">无待签署制度</div>
          </div>
        </div>
        <!-- 待整改：未闭环整改单 + 时限预警 -->
        <div class="card">
          <div class="ch"><h3>待整改</h3><span class="cnt">{{ toRemed.length }}</span><span class="sub">逾期红 · 7日内琥珀</span></div>
          <div class="cb" style="padding-top:0">
            <div v-for="r in toRemed" :key="r.id" class="td-row clk" @click="go('/internal-audit')">
              <span class="code">RO-{{ r.id }}</span><span class="td-t">{{ r.measure || r.assignee || '整改单' }}</span>
              <span class="duetag" :class="dueCls(r.dueDate)" style="margin-left:auto">{{ dueText(r.dueDate) }}</span>
            </div>
            <div v-if="!toRemed.length" class="emptyrow">无待整改任务</div>
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
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'

const todos = ref([])
const myApprovals = ref([])
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
  if (!d) return '无期限'
  const days = Math.ceil((new Date(d) - new Date()) / 86400000)
  return days < 0 ? ('逾期 ' + (-days) + ' 天') : (days === 0 ? '今日到期' : ('剩 ' + days + ' 天'))
}
async function loadGroups() {
  try {
    const [assessments, policies, remIn, remEx] = await Promise.all([
      api.get('/assessments'), api.get('/policies'),
      api.get('/remediation-orders?type=INTERNAL').catch(() => []),
      api.get('/remediation-orders?type=EXTERNAL').catch(() => [])
    ])
    toFill.value = assessments.filter((a) => a.status === 'DRAFT' || a.status === 'IN_PROGRESS')
    toSign.value = policies.filter((p) => p.status === 'EFFECTIVE')
    toRemed.value = [...remIn, ...remEx].filter((r) => r.status === 'PENDING' || r.status === 'IN_PROGRESS')
      .sort((a, b) => (a.dueDate || '9999') < (b.dueDate || '9999') ? -1 : 1)
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
</style>
