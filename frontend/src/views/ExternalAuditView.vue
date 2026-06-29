<!-- =============================================================
     外部审计页（ExternalAuditView · M3-EXT）
     说明：严格按高保真原型「驾驶舱版.html」的 #view-extaudit 区域
     1:1 复原，逐区块还原其 DOM 结构与内联 CSS：
       页头 phead（标题 + 子公司分段 seg + 登记外审任务按钮）；
       Tab 切换 tabbar（外审任务 / 外部审计发现 / 整改跟踪）；
       Tab1 外审任务：KPI 五卡 kpibar.k5 + 左右栅格 g-16-1
         · 左：外部审计任务表（按组织隔离）
         · 右：按认证体系分布 bars / 认证有效期临近 / 计划临近提醒（企微）
       Tab2 外部审计发现：发现表 + 发现按严重度（五级）bars
       Tab3 整改跟踪：整改任务表（含企微通知状态）+
         · 对外闭环漏斗 funnel（6 段：外部发现→内部整改→内部已验证
           →已向外部机构提交→外方受理→外方确认关闭，后三段为对外三段）
         · 对外回函与受理表
     配色/间距/圆角/字号全部照搬原型；颜色复用 tokens.css 语义令牌，
     漏斗后两段保留原型内联色（#3a7d9c / #2f7a8a），严重度极高/极低同理。
     文案走 i18n（zh/en 同步），静态示例数值取自原型。
     ============================================================= -->
<template>
  <AppShell>
    <section class="view">
      <!-- ===== 页头：标题 + 子公司分段 + 登记按钮 ===== -->
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('extaudit.tag') }}</div>
          <h1>{{ $t('extaudit.title') }}</h1>
        </div>
        <div class="sp"></div>
        <!-- 子公司分段（默认「集团」高亮，纯展示） -->
        <div class="seg">
          <button
            v-for="(s, i) in segs"
            :key="s"
            :class="{ on: i === activeSeg }"
            @click="activeSeg = i"
          >
            {{ $t('extaudit.seg.' + s) }}
          </button>
        </div>
        <button class="btn" :disabled="!canWrite('extaudit')" :title="canWrite('extaudit') ? '' : $t('common.noPerm')" @click="openReg">{{ $t('extaudit.register') }}</button>
      </div>

      <!-- 登记外审任务弹窗（创建 EXTERNAL 审计计划）-->
      <div v-if="showReg" class="modal-mask" @click.self="showReg = false">
        <div class="modal-card">
          <h3>{{ $t('extaudit.register') }}</h3>
          <label class="fld">审计主题 / 认证体系<input v-model="rf.title" placeholder="如 ISO 27001 年度监督审核" /></label>
          <label class="fld">计划开始日<input type="date" v-model="rf.planStartDate" /></label>
          <label class="fld">所属组织<select v-model.number="rf.orgId"><option :value="12">支付科技</option><option :value="13">消费金融</option></select></label>
          <p v-if="regErr" class="cerr">{{ regErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showReg = false">取消</button>
            <button class="btn" :disabled="!rf.title || regSaving" @click="submitReg">{{ regSaving ? '提交中…' : '确认登记' }}</button>
          </div>
        </div>
      </div>

      <!-- ===== Tab 切换 ===== -->
      <div class="tabbar">
        <button
          v-for="t in tabs"
          :key="t"
          :class="{ on: t === activeTab }"
          @click="activeTab = t"
        >
          {{ $t('extaudit.tab.' + t) }}
        </button>
      </div>

      <!-- ========== Tab1 · 外审任务 ========== -->
      <div v-show="activeTab === 'tasks'" class="tabpane">
        <!-- KPI 五卡 -->
        <div class="kpibar k5">
          <div class="kc">
            <div class="l">{{ $t('extaudit.kpi.active') }}</div>
            <div class="v" style="color: var(--accent)">4</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('extaudit.kpi.bodies') }}</div>
            <div class="v">6</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('extaudit.kpi.openFindings') }}</div>
            <div class="v" style="color: var(--danger)">11</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('extaudit.kpi.toRemed') }}</div>
            <div class="v" style="color: var(--warning)">7</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('extaudit.kpi.certPassed') }}</div>
            <div class="v" style="color: var(--success)">5</div>
          </div>
        </div>

        <div class="g g-16-1">
          <!-- 左：外部审计任务表 -->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('extaudit.tasks.title') }}</h3>
              <span class="sub">{{ $t('extaudit.tasks.sub') }}</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>{{ $t('extaudit.tasks.th.id') }}</th>
                  <th>{{ $t('extaudit.tasks.th.cert') }}</th>
                  <th>{{ $t('extaudit.tasks.th.body') }}</th>
                  <th>{{ $t('extaudit.tasks.th.owner') }}</th>
                  <th>{{ $t('extaudit.tasks.th.cycle') }}</th>
                  <th>{{ $t('extaudit.tasks.th.planStart') }}</th>
                  <th>{{ $t('extaudit.tasks.th.status') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in taskRows" :key="r.id">
                  <td class="code">{{ r.id }}</td>
                  <td><span class="pill" :class="r.pill">{{ r.cert }}</span></td>
                  <td>{{ r.body }}</td>
                  <td>{{ r.owner }}</td>
                  <td>{{ r.cycle }}</td>
                  <td class="num">{{ r.planStart }}</td>
                  <td>
                    <span class="st" :class="r.stClass"><span class="d"></span>{{ r.stText }}</span>
                  </td>
                </tr>
                <tr v-if="!taskRows.length"><td colspan="7" style="text-align:center;color:var(--text-3);padding:18px">暂无外审任务，点「登记外审任务」。</td></tr>
              </tbody>
            </table>
          </div>

          <!-- 右：分布 / 有效期 / 提醒 -->
          <div>
            <!-- 按认证体系分布 -->
            <div class="card">
              <div class="ch"><h3>{{ $t('extaudit.dist.title') }}</h3></div>
              <div class="cb">
                <div class="bars">
                  <div v-for="b in distBars" :key="b.nm" class="bar-row">
                    <div class="hd"><span class="nm">{{ b.nm }}</span><b>{{ b.v }}</b></div>
                    <div class="track">
                      <div class="seg2 a" :style="{ width: b.w }"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 认证有效期临近 -->
            <div class="card">
              <div class="ch"><h3>{{ $t('extaudit.expiry.title') }}</h3></div>
              <div class="cb">
                <div class="srow">
                  <span>{{ $t('extaudit.expiry.pci') }}</span>
                  <b style="color: var(--danger)">{{ $t('extaudit.expiry.pciLeft') }}</b>
                </div>
                <div class="srow">
                  <span>{{ $t('extaudit.expiry.iso') }}</span>
                  <b style="color: var(--warning)">{{ $t('extaudit.expiry.isoLeft') }}</b>
                </div>
                <div class="srow">
                  <span>{{ $t('extaudit.expiry.mlps') }}</span>
                  <b>{{ $t('extaudit.expiry.mlpsLeft') }}</b>
                </div>
              </div>
            </div>

            <!-- 计划临近提醒（企微） -->
            <div class="card" style="margin-top: 14px">
              <div class="ch">
                <h3>{{ $t('extaudit.remind.title') }}</h3>
                <span class="sub">{{ $t('extaudit.remind.sub') }}</span>
              </div>
              <div class="cb">
                <div class="srow">
                  <span>{{ $t('extaudit.remind.leadDays') }}</span><b>{{ $t('extaudit.remind.leadDaysV') }}</b>
                </div>
                <div class="srow">
                  <span>{{ $t('extaudit.remind.bot') }}</span><b>{{ $t('extaudit.remind.botV') }}</b>
                </div>
                <div class="srow">
                  <span>{{ $t('extaudit.remind.task') }}</span>
                  <b style="color: var(--warning)">{{ $t('extaudit.remind.taskV') }}</b>
                </div>
                <div class="srow">
                  <span>{{ $t('extaudit.remind.status') }}</span>
                  <b style="color: var(--success)">{{ $t('extaudit.remind.statusV') }}</b>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab2 · 外部审计发现 ========== -->
      <div v-show="activeTab === 'findings'" class="tabpane">
        <div class="g g-16-1">
          <!-- 发现表 -->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('extaudit.findings.title') }}</h3>
              <span class="sub">{{ $t('extaudit.findings.sub') }}</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>{{ $t('extaudit.findings.th.id') }}</th>
                  <th>{{ $t('extaudit.findings.th.source') }}</th>
                  <th>{{ $t('extaudit.findings.th.issue') }}</th>
                  <th>{{ $t('extaudit.findings.th.cert') }}</th>
                  <th>{{ $t('extaudit.findings.th.sev') }}</th>
                  <th>{{ $t('extaudit.findings.th.owner') }}</th>
                  <th>{{ $t('extaudit.findings.th.status') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in findingRows" :key="r.id">
                  <td class="code">{{ r.id }}</td>
                  <td>{{ r.source }}</td>
                  <td>{{ $t(r.issue) }}</td>
                  <td><span class="pill" :class="r.pill">{{ r.cert }}</span></td>
                  <td><span class="tag" :class="r.sevClass">{{ $t(r.sevLabel) }}</span></td>
                  <td>{{ r.owner }}</td>
                  <td>
                    <span class="st" :class="r.stClass"><span class="d"></span>{{ $t(r.stLabel) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 发现按严重度（五级） -->
          <div class="card">
            <div class="ch"><h3>{{ $t('extaudit.sevDist.title') }}</h3></div>
            <div class="cb">
              <div class="bars">
                <div v-for="b in sevBars" :key="b.label" class="bar-row">
                  <div class="hd"><span class="nm">{{ $t(b.label) }}</span><b>{{ b.v }}</b></div>
                  <div class="track">
                    <div class="seg2" :class="b.cls" :style="b.style"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab3 · 整改跟踪 ========== -->
      <div v-show="activeTab === 'remed'" class="tabpane">
        <div class="g g-16-1">
          <!-- 整改任务表（含企微通知状态） -->
          <div class="card">
            <div class="ch"><h3>{{ $t('extaudit.remTasks.title') }}</h3></div>
            <table>
              <thead>
                <tr>
                  <th>{{ $t('extaudit.remTasks.th.task') }}</th>
                  <th>{{ $t('extaudit.remTasks.th.source') }}</th>
                  <th>{{ $t('extaudit.remTasks.th.owner') }}</th>
                  <th>{{ $t('extaudit.remTasks.th.due') }}</th>
                  <th>{{ $t('extaudit.remTasks.th.notify') }}</th>
                  <th>{{ $t('extaudit.remTasks.th.status') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in remRows" :key="r.id">
                  <td class="code">{{ r.id }}</td>
                  <td>{{ r.source }}</td>
                  <td><span class="av-s">{{ r.avatar }}</span>{{ r.owner }}</td>
                  <td class="num">{{ r.due }}</td>
                  <td>
                    <span class="st" :class="r.nClass"><span class="d"></span>{{ $t(r.nLabel) }}</span>
                  </td>
                  <td>
                    <span class="st" :class="r.sClass"><span class="d"></span>{{ $t(r.sLabel) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 右：对外闭环漏斗 + 对外回函与受理 -->
          <div>
            <!-- 对外闭环漏斗（6 段，后三段为对外三段） -->
            <div class="card">
              <div class="ch"><h3>{{ $t('extaudit.funnel.title') }}</h3></div>
              <div class="cb">
                <div class="funnel">
                  <div
                    v-for="f in funnel"
                    :key="f.key"
                    class="fr"
                    :style="{ width: f.w, background: f.bg }"
                  >
                    {{ $t('extaudit.funnel.' + f.key) }} {{ f.v }}
                  </div>
                </div>
              </div>
            </div>

            <!-- 对外回函与受理 -->
            <div class="card">
              <div class="ch"><h3>{{ $t('extaudit.reply.title') }}</h3></div>
              <table>
                <thead>
                  <tr>
                    <th>{{ $t('extaudit.reply.th.audit') }}</th>
                    <th>{{ $t('extaudit.reply.th.report') }}</th>
                    <th>{{ $t('extaudit.reply.th.submitted') }}</th>
                    <th>{{ $t('extaudit.reply.th.accepted') }}</th>
                    <th>{{ $t('extaudit.reply.th.conclusion') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in replyRows" :key="r.id">
                    <td class="code">{{ r.id }}</td>
                    <td>{{ r.report }}</td>
                    <td class="num">{{ r.submitted }}</td>
                    <td>
                      <span class="st" :class="r.aClass"><span class="d"></span>{{ $t(r.aLabel) }}</span>
                    </td>
                    <td>
                      <span class="st" :class="r.cClass"><span class="d"></span>{{ $t(r.cLabel) }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
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

// ---- 顶部子公司分段（默认「集团」高亮，纯展示）----
const segs = ['all', 'pay', 'consumer', 'tech']
const activeSeg = ref(0)

// ---- Tab 切换 ----
const tabs = ['tasks', 'findings', 'remed']
const activeTab = ref('tasks')

// ---- Tab1：外部审计任务表（真实后端 /api/audit-plans?type=EXTERNAL）----
// 后端审计计划仅含 主题/类型/开始日/状态；认证体系/机构/责任单位/周期 后端未建模，显示 —。
const STATUS = {
  PLANNED: { t: '已计划', c: 'wait' }, IN_PROGRESS: { t: '实施中', c: 'doing' },
  REPORTING: { t: '待签批', c: 'wait' }, CLOSED: { t: '已关闭', c: 'ok' }, CANCELLED: { t: '已取消', c: 'over' }
}
const taskRows = ref([])
async function loadTasks() {
  try {
    const plans = await api.get('/audit-plans?type=EXTERNAL')
    taskRows.value = plans.map((p) => ({
      id: 'EA-' + p.id, pill: '', cert: p.title, body: '—', owner: '—', cycle: '—',
      planStart: p.planStartDate || '—', stClass: (STATUS[p.status] || {}).c || 'wait', stText: (STATUS[p.status] || {}).t || p.status
    }))
  } catch (e) { taskRows.value = [] }
}

// ---- 登记外审任务（真实创建 EXTERNAL 审计计划）----
const showReg = ref(false)
const regSaving = ref(false)
const regErr = ref('')
const rf = reactive({ title: '', planStartDate: '', orgId: 12 })
function openReg() { Object.assign(rf, { title: '', planStartDate: '', orgId: 12 }); regErr.value = ''; showReg.value = true }
async function submitReg() {
  regSaving.value = true; regErr.value = ''
  try {
    await api.post('/audit-plans', { orgId: rf.orgId, title: rf.title, auditType: 'EXTERNAL', planStartDate: rf.planStartDate || null })
    showReg.value = false; await loadTasks()
  } catch (e) { regErr.value = e.message } finally { regSaving.value = false }
}

onMounted(loadTasks)

// ---- 按认证体系分布（bars）----
const distBars = [
  { nm: '等保三级', v: 3, w: '100%' },
  { nm: 'ISO 27001/27701', v: 4, w: '80%' },
  { nm: 'PCI DSS', v: 2, w: '55%' },
  { nm: 'PBOC 监管检查', v: 2, w: '55%' }
]

// ---- Tab2：外部审计发现表 ----
const findingRows = [
  { id: 'EF-031', source: 'EA-2026-04', issue: 'extaudit.findings.issue.chd', pill: 'violet', cert: 'PCI', sevClass: 'vh', sevLabel: 'extaudit.sevDist.vh', owner: '支付科技', stClass: 'doing', stLabel: 'extaudit.findings.status.remediating' },
  { id: 'EF-028', source: 'EA-2026-05', issue: 'extaudit.findings.issue.access', pill: 'teal', cert: 'ISO', sevClass: 'h', sevLabel: 'extaudit.sevDist.h', owner: '集团', stClass: 'wait', stLabel: 'extaudit.findings.status.toRemed' },
  { id: 'EF-022', source: 'EA-2026-02', issue: 'extaudit.findings.issue.log', pill: '', cert: 'PBOC', sevClass: 'h', sevLabel: 'extaudit.sevDist.h', owner: '消费金融', stClass: 'doing', stLabel: 'extaudit.findings.status.remediating' },
  { id: 'EF-019', source: 'EA-2026-07', issue: 'extaudit.findings.issue.baseline', pill: 'blue', cert: '等保', sevClass: 'm', sevLabel: 'extaudit.sevDist.m', owner: '支付科技', stClass: 'wait', stLabel: 'extaudit.findings.status.toRemed' },
  { id: 'EF-014', source: 'EA-2026-01', issue: 'extaudit.findings.issue.privacy', pill: 'teal', cert: '27701', sevClass: 'l', sevLabel: 'extaudit.sevDist.l', owner: '数据科技', stClass: 'ok', stLabel: 'extaudit.findings.status.verified' }
]

// ---- 发现按严重度（五级）bars ----
// 极高/极低保留原型内联底色；高/中/低复用 seg2.h/m/l 语义色
const sevBars = [
  { label: 'extaudit.sevDist.vh', v: 1, cls: '', style: { width: '14%', background: '#7a1620' } },
  { label: 'extaudit.sevDist.h', v: 4, cls: 'h', style: { width: '55%' } },
  { label: 'extaudit.sevDist.m', v: 5, cls: 'm', style: { width: '68%' } },
  { label: 'extaudit.sevDist.l', v: 3, cls: 'l', style: { width: '40%' } },
  { label: 'extaudit.sevDist.vl', v: 1, cls: '', style: { width: '14%', background: '#8aa0b3' } }
]

// ---- Tab3：整改任务表 ----
const remRows = [
  { id: 'RT-0451', source: 'EF-031', avatar: '张', owner: '张伟', due: '06-28', nClass: 'ok', nLabel: 'extaudit.remTasks.notify.dueSoon', sClass: 'doing', sLabel: 'extaudit.remTasks.status.remediating' },
  { id: 'RT-0448', source: 'EF-022', avatar: '陈', owner: '陈强', due: '06-22', nClass: 'over', nLabel: 'extaudit.remTasks.notify.escalated', sClass: 'over', sLabel: 'extaudit.remTasks.status.overdue' },
  { id: 'RT-0442', source: 'EF-028', avatar: '王', owner: '王芳', due: '07-05', nClass: 'ok', nLabel: 'extaudit.remTasks.notify.notified', sClass: 'wait', sLabel: 'extaudit.remTasks.status.pending' }
]

// ---- 对外闭环漏斗（6 段；后三段为「对外三段」）----
// width / background 完全照搬原型内联值
const funnel = [
  { key: 'external', v: 14, w: '100%', bg: 'var(--accent)' },
  { key: 'internalFix', v: 11, w: '79%', bg: 'var(--accent-bright)' },
  { key: 'internalVerified', v: 9, w: '64%', bg: 'var(--warning)' },
  { key: 'submitted', v: 7, w: '50%', bg: '#3a7d9c' },
  { key: 'accepted', v: 5, w: '36%', bg: '#2f7a8a' },
  { key: 'closed', v: 3, w: '21%', bg: 'var(--success)' }
]

// ---- 对外回函与受理表 ----
const replyRows = [
  { id: 'EA-2026-04', report: 'v2', submitted: '06-20', aClass: 'doing', aLabel: 'extaudit.reply.accepted.underReview', cClass: 'wait', cLabel: 'extaudit.reply.conclusion.pending' },
  { id: 'EA-2026-02', report: 'v1', submitted: '06-12', aClass: 'ok', aLabel: 'extaudit.reply.accepted.accepted', cClass: 'doing', cLabel: 'extaudit.reply.conclusion.moreEvidence' },
  { id: 'EA-2026-01', report: 'v1', submitted: '05-30', aClass: 'ok', aLabel: 'extaudit.reply.accepted.accepted', cClass: 'ok', cLabel: 'extaudit.reply.conclusion.closed' }
]
</script>

<style scoped>
/* ========================================================
   样式严格对齐原型 #view-extaudit 区块及其依赖的全局 CSS。
   颜色一律走 tokens.css 语义令牌（漏斗后两段 / 严重度极高极低除外，
   按原型保留内联色）。
   ======================================================== */

/* ---- 页头 phead ---- */
.phead {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
}
.phead .kqt {
  font-size: 10.5px;
  letter-spacing: 1.5px;
  color: var(--accent);
  text-transform: uppercase;
  font-weight: 700;
  margin-bottom: 4px;
}
.phead h1 {
  font-size: 20px;
  font-weight: 760;
  letter-spacing: -0.3px;
  font-family: var(--font-display);
}
.phead .sp {
  flex: 1;
}

/* ---- 子公司分段 seg ---- */
.seg {
  display: inline-flex;
  gap: 3px;
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-md);
  padding: 3px;
  box-shadow: var(--shadow-1);
}
.seg button {
  border: 0;
  background: none;
  padding: 6px 12px;
  font-size: 11.5px;
  color: var(--text-2);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.seg button.on {
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: #fff;
  font-weight: 600;
}

/* ---- 按钮 btn ---- */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: #fff;
  border: 0;
  border-radius: var(--radius-md);
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-1);
}

/* ---- Tab 切换 tabbar / tabpane ---- */
.tabbar {
  display: inline-flex;
  gap: 2px;
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-md);
  padding: 3px;
  box-shadow: var(--shadow-1);
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.tabbar button {
  border: 0;
  background: none;
  padding: 6px 13px;
  font-size: 12px;
  color: var(--text-2);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-weight: 500;
}
.tabbar button.on {
  background: linear-gradient(135deg, var(--accent), var(--accent-strong));
  color: #fff;
  font-weight: 600;
}

/* ---- 布局栅格 g / g-16-1 ---- */
.g {
  display: grid;
  gap: 14px;
  margin-bottom: 14px;
}
.g-16-1 {
  grid-template-columns: 1.6fr 1fr;
}
@media (max-width: 980px) {
  .g-16-1 {
    grid-template-columns: 1fr;
  }
}

/* ---- KPI 五卡 kpibar.k5 ---- */
.kpibar {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 11px;
  margin-bottom: 14px;
}
.kpibar.k5 {
  grid-template-columns: repeat(5, 1fr);
}
.kc {
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  padding: 13px 13px;
  box-shadow: var(--shadow-1);
}
.kc .l {
  font-size: 11px;
  color: var(--text-2);
}
.kc .v {
  font-size: 22px;
  font-weight: 790;
  font-family: var(--font-display);
  margin-top: 5px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

/* ---- 卡片 card / 卡头 ch / 卡体 cb ---- */
.card {
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
  margin-bottom: 14px;
}
.ch {
  display: flex;
  align-items: center;
  padding: 14px 18px 4px;
}
.ch h3 {
  font-size: 14px;
  font-weight: 720;
  font-family: var(--font-display);
}
.ch .sub {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-3);
}
.cb {
  padding: 14px 18px 18px;
}

/* ---- 横向条 bars ---- */
.bars {
  display: flex;
  flex-direction: column;
  gap: 11px;
}
.bar-row .hd {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
  font-size: 12px;
}
.bar-row .hd .nm {
  color: var(--text-2);
}
.bar-row .hd b {
  font-weight: 700;
}
.track {
  height: 9px;
  background: rgba(120, 120, 120, 0.1);
  border-radius: 6px;
  display: flex;
  overflow: hidden;
  gap: 2px;
}
.seg2 {
  height: 100%;
  border-radius: 6px;
}
.seg2.h {
  background: var(--danger);
}
.seg2.m {
  background: var(--warning);
}
.seg2.l {
  background: var(--safe);
}
.seg2.a {
  background: var(--accent);
}

/* ---- 漏斗 funnel ---- */
.funnel {
  display: flex;
  flex-direction: column;
  gap: 5px;
  align-items: center;
  padding: 6px 0;
}
.funnel .fr {
  height: 30px;
  border-radius: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 11.5px;
  font-weight: 600;
}

/* ---- 信息行 srow ---- */
.srow {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 0;
  border-bottom: 1px solid var(--border-subtle);
  font-size: 11.5px;
  color: var(--text-2);
}
.srow:last-child {
  border: 0;
}
.srow b {
  color: var(--text-1);
}

/* ---- 表格 table（对齐原型全局 table 规则）---- */
table {
  width: 100%;
  border-collapse: collapse;
}
thead th {
  text-align: left;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--text-3);
  padding: 0 18px 10px;
}
tbody td {
  padding: 11px 18px;
  border-top: 1px solid var(--border-subtle);
  font-size: 12px;
}
tbody tr {
  transition: background 0.15s;
}
tbody tr:hover {
  background: var(--accent-tint);
}

/* ---- 编号 code / 数字 num ---- */
.code {
  font-weight: 700;
  color: var(--accent-strong);
  font-variant-numeric: tabular-nums;
}
.num {
  font-variant-numeric: tabular-nums;
}

/* ---- 严重度标签 tag（五级）---- */
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 700;
}
.tag::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
}
.tag.vh {
  background: #f1d9d6;
  color: #7a1620;
}
.tag.vh::before {
  background: #7a1620;
}
.tag.h {
  background: var(--danger-tint);
  color: var(--danger);
}
.tag.h::before {
  background: var(--danger);
}
.tag.m {
  background: var(--warning-tint);
  color: #a87d22;
}
.tag.m::before {
  background: var(--warning);
}
.tag.l {
  background: var(--safe-weak);
  color: var(--safe);
}
.tag.l::before {
  background: var(--safe);
}

/* ---- 认证体系标识 pill ---- */
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 600;
  background: rgba(120, 120, 120, 0.1);
  color: var(--text-2);
}
.pill.teal {
  background: var(--accent-weak);
  color: var(--accent-strong);
}
.pill.blue {
  background: var(--info-tint);
  color: var(--info);
}
.pill.violet {
  background: var(--plum-tint);
  color: var(--plum);
}

/* ---- 状态标签 st ---- */
.st {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-2);
}
.st .d {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.st.doing {
  color: var(--accent-strong);
}
.st.doing .d {
  background: var(--accent);
}
.st.over {
  color: var(--danger);
}
.st.over .d {
  background: var(--danger);
}
.st.wait .d {
  background: var(--text-3);
}
.st.ok {
  color: var(--success);
}
.st.ok .d {
  background: var(--success);
}

/* ---- 责任人头像 av-s ---- */
.av-s {
  width: 23px;
  height: 23px;
  border-radius: var(--radius-sm);
  background: var(--accent-weak);
  color: var(--accent-strong);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10.5px;
  font-weight: 700;
  margin-right: 7px;
  vertical-align: -6px;
}

/* ---- 朱砂 t-gov 表头底色（对齐原型 body.t-gov thead th）----
   按既定约定：整条选择器放进 :global(...)，避免 scoped 属性破坏主题命中。 */
:global(body.t-gov .view thead th) {
  background: var(--accent-tint);
}
/* 登记外审任务弹窗 */
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 420px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; box-sizing: border-box; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 0 0 12px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
</style>
