<template>
  <!--
    权限与职责分离（PermissionView · M8）：用户角色授权 + SoD 红线 + 例外审批两步。
    功能真源 = 后端 /api/permissions；视觉遵 tokens.css。隔离由后端 RLS。
    SoD 红线（硬阻断 BLOCK）：授予与已持角色互斥的角色 → 后端 SodViolationException(409 SOD_VIOLATION) 拒绝；
    需先「申请 SoD 例外 → 审批通过」方可放行（审批两步）。
    说明：角色(4)/SoD 规则(2)为系统级参考数据，前端按后端种子常量展示；用户取已知三账号。
  -->
  <AppShell>
    <section class="view view-perm">
      <div class="phead">
        <div><div class="kqt">{{ $t('perm.tag') }}</div><h1>权限管理</h1></div>
      </div>

      <!-- ⑧ 合并：原「权限与审批」+「权限配置」两菜单理顺为本页两个 Tab -->
      <div class="tabbar">
        <button :class="{ on: tab === 'access' }" @click="tab = 'access'">用户授权 · 职责分离 · 访问复核</button>
        <button :class="{ on: tab === 'matrix' }" @click="tab = 'matrix'">角色权限矩阵</button>
      </div>

      <!-- Tab · 角色权限矩阵（原「权限配置」）-->
      <div v-show="tab === 'matrix'">
        <RbacMatrix />
      </div>

      <!-- Tab · 用户授权 / 职责分离 / 访问复核（原「权限与审批」）-->
      <div v-show="tab === 'access'">
      <!-- SoD 规则（系统参考）-->
      <div class="card">
        <div class="ch"><h3>{{ $t('perm.sodRules') }}</h3><span class="sub">{{ $t('perm.sodRulesSub') }}</span></div>
        <div class="cb" style="padding-top: 0">
          <table>
            <thead><tr><th>{{ $t('perm.rth.pair') }}</th><th>{{ $t('perm.rth.mode') }}</th><th>{{ $t('perm.rth.desc') }}</th></tr></thead>
            <tbody>
              <tr v-for="r in SOD_RULES" :key="r.id">
                <td><span class="pill teal">{{ roleName(r.a) }}</span><span class="x">×</span><span class="pill teal">{{ roleName(r.b) }}</span></td>
                <td><span class="st" :class="r.mode === 'BLOCK' ? 'over' : 'wait'"><span class="d"></span>{{ $t('perm.mode.' + r.mode) }}</span></td>
                <td class="desc">{{ $t('perm.ruleDesc.' + r.id) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="g g-1-1">
        <!-- 用户角色授权 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('perm.grantTitle') }}</h3></div>
          <div class="cb">
            <div class="bar">
              <label>{{ $t('perm.user') }}
                <select v-model.number="selUserId" @change="onUserChange">
                  <option v-for="u in userOptions" :key="u.id" :value="u.id">{{ u.displayName || u.username || u.name }}（{{ orgName(u.orgId) }}）</option>
                </select>
              </label>
              <button class="btn ghost sm" @click="loadGrants">{{ $t('perm.query') }}</button>
            </div>

            <table v-if="grantsLoaded" style="margin-top: 6px">
              <thead><tr><th>{{ $t('perm.gth.role') }}</th><th>{{ $t('perm.gth.by') }}</th><th>{{ $t('perm.gth.state') }}</th><th>{{ $t('perm.gth.op') }}</th></tr></thead>
              <tbody>
                <tr v-for="gr in grants" :key="gr.id">
                  <td><span class="pill teal">{{ roleName(gr.roleId) }}</span></td>
                  <td class="muted">{{ gr.grantedBy || '—' }}</td>
                  <td><span class="st" :class="gr.active ? 'ok' : 'wait'"><span class="d"></span>{{ gr.active ? $t('perm.active') : $t('perm.revoked') }}</span></td>
                  <td class="ops"><button v-if="gr.active" class="btn ghost sm danger" :disabled="busy" @click="revoke(gr.roleId)">{{ $t('perm.op.revoke') }}</button></td>
                </tr>
                <tr v-if="!grants.length"><td colspan="4" class="emptyrow">{{ $t('perm.noGrant') }}</td></tr>
              </tbody>
            </table>

            <div class="bar" style="margin-top: 10px">
              <label>{{ $t('perm.grantRole') }}
                <select v-model.number="grantRoleId"><option v-for="r in roleOptions" :key="r.id" :value="r.id">{{ r.name }}</option></select>
              </label>
              <button class="btn sm" :disabled="busy" @click="grant">{{ $t('perm.op.grant') }}</button>
            </div>
            <!-- SoD 红线命中 → 高亮显示后端消息（端到端可见）-->
            <p v-if="opError" class="redline-msg">⛔ {{ opError }}</p>
            <p v-else-if="okMsg" class="ok-msg">✓ {{ okMsg }}</p>
          </div>
        </div>

        <!-- SoD 例外申请/审批 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('perm.exTitle') }}</h3><span class="sub">{{ $t('perm.exSub') }}</span></div>
          <div class="cb">
            <div class="bar">
              <label>{{ $t('perm.exRule') }}
                <select v-model.number="exRuleId"><option v-for="r in SOD_RULES" :key="r.id" :value="r.id">{{ roleName(r.a) }}×{{ roleName(r.b) }}</option></select>
              </label>
            </div>
            <label class="fld">{{ $t('perm.exReason') }}<input v-model="exReason" :placeholder="$t('perm.exReasonPh')" /></label>
            <button class="btn sm" :disabled="!exReason || busy" @click="requestException">{{ $t('perm.op.requestEx') }}</button>

            <table v-if="exceptions.length" style="margin-top: 12px">
              <thead><tr><th>#</th><th>{{ $t('perm.exth.rule') }}</th><th>{{ $t('perm.exth.user') }}</th><th>{{ $t('perm.exth.status') }}</th><th>{{ $t('perm.exth.op') }}</th></tr></thead>
              <tbody>
                <tr v-for="e in exceptions" :key="e.id">
                  <td class="code">#{{ e.id }}</td>
                  <td>{{ ruleLabel(e.sodRuleId) }}</td>
                  <td>{{ userName(e.userId) }}</td>
                  <td><span class="st" :class="EX_CLS[e.status]"><span class="d"></span>{{ $t('perm.exStatus.' + e.status) }}</span></td>
                  <td class="ops">
                    <template v-if="e.status === 'PENDING'">
                      <button class="btn sm" :disabled="busy" @click="decideEx(e, 'approve')">{{ $t('perm.op.approveEx') }}</button>
                      <button class="btn ghost sm danger" :disabled="busy" @click="decideEx(e, 'reject')">{{ $t('perm.op.rejectEx') }}</button>
                    </template>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="hint">{{ $t('perm.exEmpty') }}</p>
            <p v-if="exError" class="cerr">{{ exError }}</p>
          </div>
        </div>
      </div>

      <!-- 访问复核（UAR · 周期性权限再认证）-->
      <div class="card">
        <div class="ch">
          <h3>{{ $t('perm.uar.title') }}</h3>
          <span class="sub">{{ $t('perm.uar.sub') }}</span>
        </div>
        <div class="cb">
          <div class="bar">
            <label>{{ $t('perm.uar.period') }}<input v-model="uarForm.period" placeholder="2026H1" /></label>
            <label>{{ $t('perm.uar.reviewer') }}<input v-model="uarForm.reviewer" :placeholder="$t('perm.uar.reviewerPh')" /></label>
            <label>{{ $t('perm.uar.org') }}
              <select v-model.number="uarForm.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select>
            </label>
            <button class="btn sm" :disabled="!uarForm.period || busy" @click="createReview">{{ $t('perm.uar.create') }}</button>
          </div>

          <div class="g g-1-1" style="margin-top: 14px">
            <!-- 复核批次（本会期）-->
            <div>
              <div class="mini-h">{{ $t('perm.uar.batches') }}</div>
              <table>
                <thead><tr><th>#</th><th>{{ $t('perm.uar.th.period') }}</th><th>{{ $t('perm.uar.th.status') }}</th><th>{{ $t('perm.uar.th.op') }}</th></tr></thead>
                <tbody>
                  <tr v-for="r in reviews" :key="r.id" class="clk" :class="{ on: r.id === selReviewId }" @click="selectReview(r)">
                    <td class="code">#{{ r.id }}</td>
                    <td>{{ r.period }}</td>
                    <td><span class="st" :class="UAR_CLS[r.status]"><span class="d"></span>{{ $t('perm.uar.status.' + r.status) }}</span></td>
                    <td class="ops" @click.stop>
                      <button v-if="r.status === 'OPEN'" class="btn sm" :disabled="busy" @click="reviewAct(r, 'start')">{{ $t('perm.uar.op.start') }}</button>
                      <button v-else-if="r.status === 'IN_REVIEW'" class="btn ghost sm" :disabled="busy" @click="reviewAct(r, 'complete')">{{ $t('perm.uar.op.complete') }}</button>
                    </td>
                  </tr>
                  <tr v-if="!reviews.length"><td colspan="4" class="emptyrow">{{ $t('perm.uar.empty') }}</td></tr>
                </tbody>
              </table>
            </div>
            <!-- 审阅项（选中批次）-->
            <div>
              <div class="mini-h">{{ $t('perm.uar.items') }}</div>
              <div v-if="!selReviewId" class="hint">{{ $t('perm.uar.selectHint') }}</div>
              <table v-else>
                <thead><tr><th>{{ $t('perm.uar.ith.grant') }}</th><th>{{ $t('perm.uar.ith.decision') }}</th><th>{{ $t('perm.uar.ith.op') }}</th></tr></thead>
                <tbody>
                  <tr v-for="it in reviewItems" :key="it.id">
                    <td class="code">{{ $t('perm.uar.grantRef', { id: it.userRoleOrgId }) }}</td>
                    <td><span class="st" :class="DECISION_CLS[it.decision]"><span class="d"></span>{{ $t('perm.uar.decision.' + it.decision) }}</span></td>
                    <td class="ops">
                      <template v-if="it.decision === 'PENDING'">
                        <button class="btn sm" :disabled="busy" @click="decideItem(it, 'KEEP')">{{ $t('perm.uar.op.keep') }}</button>
                        <button class="btn ghost sm danger" :disabled="busy" @click="decideItem(it, 'REVOKE')">{{ $t('perm.uar.op.revoke') }}</button>
                      </template>
                    </td>
                  </tr>
                  <tr v-if="!reviewItems.length"><td colspan="3" class="emptyrow">{{ $t('perm.uar.itemEmpty') }}</td></tr>
                </tbody>
              </table>
            </div>
          </div>
          <p v-if="uarError" class="cerr">{{ uarError }}</p>
        </div>
      </div>
      </div><!-- /tab access -->
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import RbacMatrix from '@/components/RbacMatrix.vue'
import { api } from '@/api/client.js'
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()

// ⑧ 合并：本页两 Tab（access=用户授权/SoD/UAR；matrix=角色权限矩阵）
const tab = ref('access')

// ---- 系统级参考数据（对齐后端种子：role / sod_rule / app_user）----
const ROLES = [
  { id: 1, code: 'MAKER', name: '发起人' },
  { id: 2, code: 'CHECKER', name: '审批人' },
  { id: 3, code: 'RISK_OWNER', name: '风险责任人' },
  { id: 4, code: 'AUDITOR', name: '审计员' }
]
const USERS = [
  { id: 1, name: 'group_admin', orgId: 1 },
  { id: 2, name: 'pay_user', orgId: 12 },
  { id: 3, name: 'cf_user', orgId: 13 }
]
const SOD_RULES = [
  { id: 1, a: 1, b: 2, mode: 'BLOCK' },
  { id: 2, a: 3, b: 4, mode: 'DETECT' }
]
// 全量角色/用户（从后端 /api/rbac 加载，使授权可选所有角色——含超管/风险专员/自定义；静态表为兜底）
const allRoles = ref([])
const allUsers = ref([])
const roleName = (id) => (allRoles.value.find((r) => r.id === id) || ROLES.find((r) => r.id === id) || {}).name || ('#' + id)
const userName = (id) => {
  const u = allUsers.value.find((x) => x.id === id) || USERS.find((x) => x.id === id)
  return u ? (u.displayName || u.username || u.name) : '#' + id
}
const orgName = (orgId) => ({ 1: '集团', 12: '支付科技', 13: '消费金融' }[orgId] || ('org' + orgId))
const ruleLabel = (id) => { const r = SOD_RULES.find((x) => x.id === id); return r ? roleName(r.a) + '×' + roleName(r.b) : '#' + id }

const selUserId = ref(2) // 默认 pay_user
// 下拉数据源：优先后端全量，兜底静态
const userOptions = computed(() => (allUsers.value.length ? allUsers.value : USERS))
const roleOptions = computed(() => (allRoles.value.length ? allRoles.value : ROLES))
const selOrgId = () => (userOptions.value.find((u) => u.id === selUserId.value) || {}).orgId

// 加载全量角色/用户（用户授权可选所有角色与用户）
onMounted(async () => {
  try { allRoles.value = await api.get('/rbac/roles') } catch (e) { /* 兜底静态 */ }
  try { allUsers.value = await api.get('/rbac/users') } catch (e) { /* 兜底静态 */ }
})
const grants = ref([])
const grantsLoaded = ref(false)
const grantRoleId = ref(1)
const busy = ref(false)
const opError = ref('')
const okMsg = ref('')

function onUserChange() { grantsLoaded.value = false; grants.value = []; opError.value = ''; okMsg.value = '' }

async function loadGrants() {
  opError.value = ''; okMsg.value = ''
  try {
    grants.value = await api.get('/permissions/user-roles?orgId=' + selOrgId() + '&userId=' + selUserId.value)
    grantsLoaded.value = true
  } catch (e) { opError.value = e.message }
}
async function grant() {
  busy.value = true; opError.value = ''; okMsg.value = ''
  try {
    await api.post('/permissions/grant', { orgId: selOrgId(), userId: selUserId.value, roleId: grantRoleId.value })
    okMsg.value = '已授予「' + roleName(grantRoleId.value) + '」'
    await loadGrants()
  } catch (e) { opError.value = e.message } finally { busy.value = false }
}
async function revoke(roleId) {
  busy.value = true; opError.value = ''; okMsg.value = ''
  try { await api.post('/permissions/revoke', { orgId: selOrgId(), userId: selUserId.value, roleId }); await loadGrants() }
  catch (e) { opError.value = e.message } finally { busy.value = false }
}

// ---- SoD 例外申请/审批（无 list 端点 → 本会期跟踪 POST 返回的例外）----
const EX_CLS = { PENDING: 'wait', APPROVED: 'ok', REJECTED: 'over' }
const exRuleId = ref(1)
const exReason = ref('')
const exceptions = ref([])
const exError = ref('')

async function requestException() {
  busy.value = true; exError.value = ''
  try {
    const ex = await api.post('/permissions/sod-exceptions', { orgId: selOrgId(), userId: selUserId.value, sodRuleId: exRuleId.value, reason: exReason.value })
    exceptions.value = [ex, ...exceptions.value.filter((e) => e.id !== ex.id)]
    exReason.value = ''
  } catch (e) { exError.value = e.message } finally { busy.value = false }
}
async function decideEx(e, op) {
  busy.value = true; exError.value = ''
  try {
    const updated = await api.post('/permissions/sod-exceptions/' + e.id + '/' + op, {})
    exceptions.value = exceptions.value.map((x) => (x.id === e.id ? updated : x))
  } catch (err) { exError.value = err.message } finally { busy.value = false }
}

// ---- 访问复核 UAR（无 list-all 端点 → 本会期跟踪 create 返回的批次）----
const UAR_CLS = { OPEN: 'wait', IN_REVIEW: 'doing', COMPLETED: 'ok' }
const DECISION_CLS = { PENDING: 'wait', KEEP: 'ok', REVOKE: 'over' }
const uarForm = reactive({ period: '2026H1', reviewer: '', orgId: 12 })
const reviews = ref([])
const selReviewId = ref(null)
const reviewItems = ref([])
const uarError = ref('')

async function createReview() {
  busy.value = true; uarError.value = ''
  try {
    const r = await api.post('/access-reviews', { orgId: uarForm.orgId, period: uarForm.period, reviewer: uarForm.reviewer })
    reviews.value = [r, ...reviews.value.filter((x) => x.id !== r.id)]
  } catch (e) { uarError.value = e.message } finally { busy.value = false }
}
async function selectReview(r) {
  selReviewId.value = r.id
  try { reviewItems.value = await api.get('/access-reviews/' + r.id + '/items') } catch (e) { reviewItems.value = [] }
}
// 批次流转：start(快照有效授权为审阅项) / complete
async function reviewAct(r, op) {
  busy.value = true; uarError.value = ''
  try {
    const updated = await api.post('/access-reviews/' + r.id + '/' + op, {})
    reviews.value = reviews.value.map((x) => (x.id === r.id ? updated : x))
    if (selReviewId.value === r.id) await selectReview({ id: r.id })
  } catch (e) { uarError.value = e.message } finally { busy.value = false }
}
// 审阅项决定：KEEP / REVOKE
async function decideItem(it, decision) {
  busy.value = true; uarError.value = ''
  try {
    await api.post('/access-reviews/items/' + it.id + '/decide', { decision })
    if (selReviewId.value) await selectReview({ id: selReviewId.value })
  } catch (e) { uarError.value = e.message } finally { busy.value = false }
}
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.g { display: grid; gap: 14px; }
.g-1-1 { grid-template-columns: 1fr 1fr; }
@media (max-width: 980px) { .g-1-1 { grid-template-columns: 1fr; } }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.ghost.danger { color: var(--danger); border-color: var(--danger); }
.btn.sm { padding: 5px 12px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); margin-bottom: 14px; }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 4px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .sub { margin-left: auto; font-size: 11px; color: var(--text-3); }
.cb { padding: 14px 18px 18px; }
.bar { display: flex; align-items: flex-end; gap: 10px; flex-wrap: wrap; }
.bar label, .fld { display: block; font-size: 12px; color: var(--text-2); }
.bar select, .fld input { display: block; margin-top: 5px; height: 36px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13px; font-family: inherit; outline: none; }
.fld { margin: 12px 0; }
.fld input { width: 100%; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 14px 10px; }
tbody td { padding: 9px 14px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
td.ops { display: flex; gap: 6px; align-items: center; }
.code { font-weight: 700; color: var(--accent-strong); }
.muted { color: var(--text-3); font-size: 11.5px; }
.desc { color: var(--text-2); }
.x { margin: 0 6px; color: var(--text-3); font-weight: 700; }
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: rgba(120,120,120,0.1); color: var(--text-2); }
.pill.teal { background: var(--accent-weak); color: var(--accent-strong); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.over { color: var(--danger); } .st.over .d { background: var(--danger); }
.st.wait .d { background: var(--text-3); }
.emptyrow { text-align: center; color: var(--text-2); padding: 16px 0; }
.hint { color: var(--text-3); font-size: 12px; padding: 10px 0 0; }
.mini-h { font-size: 11.5px; font-weight: 700; color: var(--text-2); margin-bottom: 8px; }
tbody tr.clk { cursor: pointer; }
tbody tr.on { background: var(--accent-tint); }
.cerr { color: var(--danger); font-size: 12.5px; margin: 8px 0 0; }
.redline-msg { margin: 12px 0 0; padding: 9px 12px; font-size: 12.5px; font-weight: 600; color: var(--danger); background: var(--danger-tint, rgba(180,35,45,0.1)); border: 1px solid var(--danger); border-radius: var(--radius-md); }
.ok-msg { margin: 12px 0 0; padding: 9px 12px; font-size: 12.5px; font-weight: 600; color: var(--success); background: var(--success-tint, rgba(40,150,90,0.1)); border: 1px solid var(--success); border-radius: var(--radius-md); }
/* ⑧ Tab 切换条 */
.tabbar { display: flex; gap: 6px; margin-bottom: 14px; border-bottom: 1px solid var(--surface-border); }
.tabbar button { border: 0; background: none; color: var(--text-2); font-size: 13px; font-weight: 600; padding: 9px 14px; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; font-family: inherit; }
.tabbar button:hover { color: var(--text-1); }
.tabbar button.on { color: var(--accent-strong); border-bottom-color: var(--accent-strong); }
</style>
