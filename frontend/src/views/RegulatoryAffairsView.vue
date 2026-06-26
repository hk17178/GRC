<!-- =============================================================
     监管事项页（RegulatoryAffairsView · M11）
     说明：严格按高保真原型「驾驶舱版.html」的 #view-regaffairs 区域
     1:1 复原，逐区块还原其 DOM 结构与内联 CSS：
       页头 phead（标题 + 子公司分段 seg + 登记监管事项按钮）；
       KPI 五卡 kpibar.k5（本月待报送 / 逾期报送 / 未结问询 /
         处罚整改未闭环 / 重大事件报送）；
       Tab 切换 tabbar（报送日历 / 年度合规计划 / 监管问询 /
         处罚与约谈 / 重大事件报送，共 5 个）；
       Tab1 报送日历：左右栅格 g-16-1
         · 左：监管报送日历表（法定时限）
         · 右：报送达成（本年）bars + 临近报送 srow
       Tab5 年度合规计划：KPI 四卡 kpibar.k4 + 2026 年度合规计划表
       Tab2 监管问询：监管问询表（含答复留痕、状态）
       Tab3 处罚与约谈：处罚与约谈台账表（约谈 / 行政处罚 tag）
       Tab4 重大事件报送：infoline 提示 + 空态 empty
     注意：原型 tabbar 中 Tab 顺序为
       报送日历 → 年度合规计划 → 监管问询 → 处罚与约谈 → 重大事件报送，
       照此顺序还原（pane 的 data-pane 编号 g1/g5/g2/g3/g4 仅原型内部 id，
       本组件按 Tab 显示顺序组织）。
     配色/间距/圆角/字号全部照搬原型；颜色复用 tokens.css 语义令牌。
     文案走 i18n（zh/en 同步），静态示例数值取自原型。
     ============================================================= -->
<template>
  <AppShell>
    <section class="view view-regaffairs">
      <!-- ===== 页头：标题 + 子公司分段 + 登记按钮 ===== -->
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('regaffairs.tag') }}</div>
          <h1>{{ $t('regaffairs.title') }}</h1>
        </div>
        <div class="sp"></div>
        <!-- 子公司分段（默认「集团」高亮，纯展示交互）-->
        <div class="seg">
          <button
            v-for="(s, i) in segs"
            :key="s"
            :class="{ on: i === activeSeg }"
            @click="activeSeg = i"
          >
            {{ $t('regaffairs.seg.' + s) }}
          </button>
        </div>
        <button class="btn" :disabled="!canWrite('regaffairs')" :title="canWrite('regaffairs') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('regaffairs.register') }}</button>
      </div>

      <!-- 登记弹窗：登记监管报送 → POST /api/reg-filings → 刷新报送日历（端到端写）。
           说明：监管事项含 4 台账，此登记入口先覆盖最主线的「报送日历」；
           其余台账(问询/处罚/重大事件)的登记入口按需在各自 Tab 增设。 -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('regaffairs.create.title') }}</h3>
          <label class="fld">{{ $t('regaffairs.create.item') }}
            <input v-model="form.title" :placeholder="$t('regaffairs.create.itemPh')" />
          </label>
          <label class="fld">{{ $t('regaffairs.create.regulator') }}
            <input v-model="form.regulator" :placeholder="$t('regaffairs.create.regulatorPh')" />
          </label>
          <label class="fld">{{ $t('regaffairs.create.deadline') }}
            <input v-model="form.statutoryDeadline" type="date" />
          </label>
          <label class="fld">{{ $t('regaffairs.create.org') }}
            <select v-model="form.orgId">
              <option :value="12">{{ $t('regaffairs.create.orgPay') }}</option>
              <option :value="13">{{ $t('regaffairs.create.orgConsumer') }}</option>
            </select>
          </label>
          <p v-if="createError" class="cerr">{{ createError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!form.title || !form.statutoryDeadline || saving" @click="submitCreate">
              {{ saving ? $t('common.submitting') : $t('regaffairs.create.confirm') }}
            </button>
          </div>
        </div>
      </div>

      <!-- ===== KPI 五卡（能派生的取真实计数，派生不了显「—」）===== -->
      <div class="kpibar k5">
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.dueMonth') }}</div>
          <!-- 待报送数 = reg-filings 中 status∈{TO_DRAFT,DRAFTING} 条数 -->
          <div class="v" style="color: var(--accent)">{{ kpiDueToSubmit }}</div>
          <div class="s">{{ $t('regaffairs.kpi.dueMonthSub') }}</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.overdue') }}</div>
          <!-- 逾期报送：后端无逾期标记字段，无法派生 → 「—」 -->
          <div class="v" style="color: var(--danger)">{{ $t('regaffairs.dash') }}</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.openInquiry') }}</div>
          <!-- 未结问询 = reg-inquiries 中 status≠CLOSED 条数 -->
          <div class="v" style="color: var(--warning)">{{ kpiOpenInquiry }}</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.penaltyOpen') }}</div>
          <!-- 处罚整改未闭环 = reg-penalties 中 status≠CLOSED 条数 -->
          <div class="v" style="color: var(--danger)">{{ kpiPenaltyOpen }}</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.majorReport') }}</div>
          <!-- 重大事件报送 = major-incidents 总条数 -->
          <div class="v">{{ kpiMajorCount }}</div>
          <div class="s">{{ $t('regaffairs.kpi.majorReportSub') }}</div>
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
          {{ $t('regaffairs.tab.' + t) }}
        </button>
      </div>

      <!-- ========== Tab1 · 报送日历 ========== -->
      <div v-show="activeTab === 'calendar'" class="tabpane">
        <div class="g g-16-1">
          <!-- 左：监管报送日历表（真调 GET /api/reg-filings）-->
          <!-- 字段以后端为准：id/orgId/title/regulator/statutoryDeadline/status；
               原型的「类型(月报/季报)/责任人/回执留痕」后端暂无 → 显「—」(DM-5 C 缺口) -->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('regaffairs.calendar.title') }}</h3>
              <span class="sub">{{ $t('regaffairs.calendar.sub') }}</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>{{ $t('regaffairs.calendar.th.item') }}</th>
                  <th>{{ $t('regaffairs.calendar.th.regulator') }}</th>
                  <th>{{ $t('regaffairs.calendar.th.type') }}</th>
                  <th>{{ $t('regaffairs.calendar.th.deadline') }}</th>
                  <th>{{ $t('regaffairs.calendar.th.owner') }}</th>
                  <th>{{ $t('regaffairs.calendar.th.status') }}</th>
                  <th>{{ $t('regaffairs.calendar.th.op') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in filings" :key="r.id">
                  <td>{{ r.title || $t('regaffairs.dash') }}</td>
                  <td>{{ r.regulator || $t('regaffairs.dash') }}</td>
                  <!-- 类型后端暂无 → 「—」 -->
                  <td>{{ $t('regaffairs.dash') }}</td>
                  <td class="num">{{ r.statutoryDeadline || $t('regaffairs.dash') }}</td>
                  <!-- 责任人后端暂无 → 「—」 -->
                  <td>{{ $t('regaffairs.dash') }}</td>
                  <td>
                    <span class="st" :class="filingStCls(r.status)"><span class="d"></span>{{ filingStLabel(r.status) }}</span>
                  </td>
                  <!-- 报送审批两步化：准备 → 提交评审 → 审批通过/驳回 → 关闭（仿 CR-002）-->
                  <td class="ops">
                    <button v-if="r.status === 'TO_DRAFT'" class="btn ghost sm" :disabled="filingBusy === r.id" @click="filingAct(r, 'prepare')">{{ $t('regaffairs.filingOp.prepare') }}</button>
                    <button v-else-if="r.status === 'DRAFTING'" class="btn sm" :disabled="filingBusy === r.id" @click="filingAct(r, 'submit-for-review')">{{ $t('regaffairs.filingOp.submit') }}</button>
                    <template v-else-if="r.status === 'PENDING_REVIEW'">
                      <button class="btn sm" :disabled="filingBusy === r.id" @click="filingAct(r, 'approve-submit')">{{ $t('regaffairs.filingOp.approve') }}</button>
                      <button class="btn ghost sm danger" :disabled="filingBusy === r.id" @click="filingAct(r, 'reject-submit')">{{ $t('regaffairs.filingOp.reject') }}</button>
                    </template>
                    <button v-else-if="r.status === 'SUBMITTED'" class="btn ghost sm" :disabled="filingBusy === r.id" @click="filingAct(r, 'close')">{{ $t('regaffairs.filingOp.close') }}</button>
                    <span v-else class="muted">{{ $t('regaffairs.dash') }}</span>
                  </td>
                </tr>
                <tr v-if="!filings.length">
                  <td colspan="7" class="emptyrow">{{ filingsError || $t('regaffairs.emptyRow') }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 右：报送达成 + 临近报送（后端无对应聚合/字段，不臆造数据，显「—」）-->
          <div>
            <div class="card">
              <div class="ch"><h3>{{ $t('regaffairs.achieve.title') }}</h3></div>
              <div class="cb">
                <div class="empty">
                  <div class="d">{{ $t('regaffairs.dash') }}</div>
                </div>
              </div>
            </div>
            <div class="card">
              <div class="ch"><h3>{{ $t('regaffairs.upcoming.title') }}</h3></div>
              <div class="cb">
                <div class="empty">
                  <div class="d">{{ $t('regaffairs.dash') }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab2 · 年度合规计划（真实后端 GET /api/compliance-plans）==========
           后端 M11 年度计划实体已建（CompliancePlan + Item，状态机 DRAFT→ACTIVE→CLOSED）；
           左=计划列表（年度/名称/责任人/状态 + 启用/关闭），点选 → 右=该计划事项（事项/部门/期限/状态可改）。 -->
      <div v-show="activeTab === 'plan'" class="tabpane">
        <div class="g g-16-1">
          <!-- 左：年度计划列表 -->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('regaffairs.plan.list') }}</h3>
              <button class="btn ghost sm" style="margin-left: auto" :disabled="!canWrite('regaffairs')" @click="openPlanCreate">{{ $t('regaffairs.plan.create.btn') }}</button>
            </div>
            <table>
              <thead><tr>
                <th>{{ $t('regaffairs.plan.th.year') }}</th><th>{{ $t('regaffairs.plan.th.title') }}</th>
                <th>{{ $t('regaffairs.plan.th.owner') }}</th><th>{{ $t('regaffairs.plan.th.status') }}</th><th>{{ $t('regaffairs.plan.th.op') }}</th>
              </tr></thead>
              <tbody>
                <tr v-for="p in plans" :key="p.id" class="clk" :class="{ on: p.id === selPlanId }" @click="selectPlan(p)">
                  <td class="num">{{ p.year }}</td>
                  <td>{{ p.title }}</td>
                  <td>{{ p.owner || '—' }}</td>
                  <td><span class="st" :class="PLAN_CLS[p.status]"><span class="d"></span>{{ $t('regaffairs.plan.status.' + p.status) }}</span></td>
                  <td class="ops" @click.stop>
                    <button v-if="p.status === 'DRAFT'" class="btn sm" :disabled="planBusy === p.id" @click="planAct(p, 'activate')">{{ $t('regaffairs.plan.op.activate') }}</button>
                    <button v-else-if="p.status === 'ACTIVE'" class="btn ghost sm" :disabled="planBusy === p.id" @click="planAct(p, 'close')">{{ $t('regaffairs.plan.op.close') }}</button>
                  </td>
                </tr>
                <tr v-if="!plans.length"><td colspan="5" class="emptyrow">{{ plansError || $t('regaffairs.plan.empty') }}</td></tr>
              </tbody>
            </table>
          </div>

          <!-- 右：选中计划的事项 -->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('regaffairs.plan.items') }}</h3>
              <button v-if="selPlanId" class="btn ghost sm" style="margin-left: auto" @click="openItemCreate">{{ $t('regaffairs.plan.addItem') }}</button>
            </div>
            <div v-if="!selPlanId" class="hint">{{ $t('regaffairs.plan.selectHint') }}</div>
            <table v-else>
              <thead><tr>
                <th>#</th><th>{{ $t('regaffairs.plan.ith.matter') }}</th><th>{{ $t('regaffairs.plan.ith.dept') }}</th>
                <th>{{ $t('regaffairs.plan.ith.due') }}</th><th>{{ $t('regaffairs.plan.ith.status') }}</th>
              </tr></thead>
              <tbody>
                <tr v-for="it in planItems" :key="it.id">
                  <td class="num">{{ it.seq }}</td>
                  <td>{{ it.matter }}</td>
                  <td>{{ it.ownerDept || '—' }}</td>
                  <td class="num">{{ it.dueDate || '—' }}</td>
                  <td>
                    <select class="mini-sel" :value="it.status" :disabled="itemBusy === it.id" @change="setItemStatus(it, $event.target.value)">
                      <option value="PENDING">{{ $t('regaffairs.plan.istatus.PENDING') }}</option>
                      <option value="IN_PROGRESS">{{ $t('regaffairs.plan.istatus.IN_PROGRESS') }}</option>
                      <option value="DONE">{{ $t('regaffairs.plan.istatus.DONE') }}</option>
                    </select>
                  </td>
                </tr>
                <tr v-if="!planItems.length"><td colspan="5" class="emptyrow">{{ $t('regaffairs.plan.itemEmpty') }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>
        <p v-if="planOpError" class="cerr" style="padding: 0 4px">{{ planOpError }}</p>
      </div>

      <!-- 登记年度计划弹窗 -->
      <div v-if="showPlanCreate" class="modal-mask" @click.self="showPlanCreate = false">
        <div class="modal-card">
          <h3>{{ $t('regaffairs.plan.create.btn') }}</h3>
          <label class="fld">{{ $t('regaffairs.plan.th.year') }}<input v-model.number="planForm.year" type="number" /></label>
          <label class="fld">{{ $t('regaffairs.plan.th.title') }}<input v-model="planForm.title" /></label>
          <label class="fld">{{ $t('regaffairs.plan.th.owner') }}<input v-model="planForm.owner" /></label>
          <label class="fld">{{ $t('regaffairs.create.org') }}
            <select v-model.number="planForm.orgId"><option :value="12">{{ $t('regaffairs.create.orgPay') }}</option><option :value="13">{{ $t('regaffairs.create.orgConsumer') }}</option></select>
          </label>
          <p v-if="planOpError" class="cerr">{{ planOpError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showPlanCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!planForm.year || !planForm.title || planSaving" @click="submitPlan">{{ planSaving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
      </div>

      <!-- 新增计划事项弹窗 -->
      <div v-if="showItemCreate" class="modal-mask" @click.self="showItemCreate = false">
        <div class="modal-card">
          <h3>{{ $t('regaffairs.plan.addItem') }}</h3>
          <label class="fld">{{ $t('regaffairs.plan.ith.matter') }}<input v-model="itemForm.matter" /></label>
          <label class="fld">{{ $t('regaffairs.plan.ith.dept') }}<input v-model="itemForm.ownerDept" /></label>
          <label class="fld">{{ $t('regaffairs.plan.ith.due') }}<input v-model="itemForm.dueDate" type="date" /></label>
          <p v-if="planOpError" class="cerr">{{ planOpError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showItemCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!itemForm.matter || planSaving" @click="submitItem">{{ planSaving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
      </div>

      <!-- ========== Tab3 · 监管问询 ========== -->
      <div v-show="activeTab === 'inquiry'" class="tabpane">
        <div class="card">
          <div class="ch">
            <h3>{{ $t('regaffairs.inquiry.title') }}</h3>
            <span class="more">{{ $t('regaffairs.inquiry.add') }}</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>{{ $t('regaffairs.inquiry.th.id') }}</th>
                <th>{{ $t('regaffairs.inquiry.th.regulator') }}</th>
                <th>{{ $t('regaffairs.inquiry.th.subject') }}</th>
                <th>{{ $t('regaffairs.inquiry.th.received') }}</th>
                <th>{{ $t('regaffairs.inquiry.th.replyDue') }}</th>
                <th>{{ $t('regaffairs.inquiry.th.replyLog') }}</th>
                <th>{{ $t('regaffairs.inquiry.th.status') }}</th>
              </tr>
            </thead>
            <!-- 真调 GET /api/reg-inquiries：id/title/regulator/receivedDate/dueDate/status；
                 「答复留痕」后端暂无 → 「—」 -->
            <tbody>
              <tr v-for="r in inquiries" :key="r.id">
                <td class="code">{{ r.id }}</td>
                <td>{{ r.regulator || $t('regaffairs.dash') }}</td>
                <td>{{ r.title || $t('regaffairs.dash') }}</td>
                <td class="num">{{ r.receivedDate || $t('regaffairs.dash') }}</td>
                <td class="num">{{ r.dueDate || $t('regaffairs.dash') }}</td>
                <!-- 答复留痕后端暂无 → 「—」 -->
                <td>{{ $t('regaffairs.dash') }}</td>
                <td>
                  <span class="st" :class="inquiryStCls(r.status)"><span class="d"></span>{{ inquiryStLabel(r.status) }}</span>
                </td>
              </tr>
              <tr v-if="!inquiries.length">
                <td colspan="7" class="emptyrow">{{ inquiriesError || $t('regaffairs.emptyRow') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ========== Tab4 · 处罚与约谈 ========== -->
      <div v-show="activeTab === 'penalty'" class="tabpane">
        <div class="card">
          <div class="ch">
            <h3>{{ $t('regaffairs.penalty.title') }}</h3>
            <span class="more">{{ $t('regaffairs.penalty.add') }}</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>{{ $t('regaffairs.penalty.th.id') }}</th>
                <th>{{ $t('regaffairs.penalty.th.type') }}</th>
                <th>{{ $t('regaffairs.penalty.th.regulator') }}</th>
                <th>{{ $t('regaffairs.penalty.th.reason') }}</th>
                <th>{{ $t('regaffairs.penalty.th.date') }}</th>
                <th>{{ $t('regaffairs.penalty.th.remediation') }}</th>
                <th>{{ $t('regaffairs.penalty.th.replyStatus') }}</th>
              </tr>
            </thead>
            <!-- 真调 GET /api/reg-penalties：id/title/regulator/penaltyType/amount/occurredDate/status；
                 「事由/整改要求/回函状态」后端暂无 → 「—」。
                 注：原型的「事由」列以后端 title 填充（最贴近的真实字段），整改要求列「—」-->
            <tbody>
              <tr v-for="r in penalties" :key="r.id">
                <td class="code">{{ r.id }}</td>
                <!-- penaltyType 为后端自由文本，按纯文本呈现，不臆造严重度标签色 -->
                <td>{{ r.penaltyType || $t('regaffairs.dash') }}</td>
                <td>{{ r.regulator || $t('regaffairs.dash') }}</td>
                <td>{{ r.title || $t('regaffairs.dash') }}</td>
                <td class="num">{{ r.occurredDate || $t('regaffairs.dash') }}</td>
                <!-- 整改要求后端暂无 → 「—」 -->
                <td>{{ $t('regaffairs.dash') }}</td>
                <td>
                  <span class="st" :class="penaltyStCls(r.status)"><span class="d"></span>{{ penaltyStLabel(r.status) }}</span>
                </td>
              </tr>
              <tr v-if="!penalties.length">
                <td colspan="7" class="emptyrow">{{ penaltiesError || $t('regaffairs.emptyRow') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ========== Tab5 · 重大事件报送 ========== -->
      <!-- 真调 GET /api/major-incidents：id/title/severity/occurredAt/reportedAt/status -->
      <div v-show="activeTab === 'major'" class="tabpane">
        <!-- 提示信息行 infoline -->
        <div class="infoline">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9" /></svg>
          {{ $t('regaffairs.major.info') }}
        </div>
        <div class="card">
          <table>
            <thead>
              <tr>
                <th>{{ $t('regaffairs.inquiry.th.id') }}</th>
                <th>{{ $t('regaffairs.calendar.th.item') }}</th>
                <th>{{ $t('regaffairs.major.thSeverity') }}</th>
                <th>{{ $t('regaffairs.major.thOccurred') }}</th>
                <th>{{ $t('regaffairs.major.thReported') }}</th>
                <th>{{ $t('regaffairs.calendar.th.status') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in majors" :key="r.id">
                <td class="code">{{ r.id }}</td>
                <td>{{ r.title || $t('regaffairs.dash') }}</td>
                <td>{{ r.severity ? $t('regaffairs.severity.' + r.severity) : $t('regaffairs.dash') }}</td>
                <td class="num">{{ r.occurredAt || $t('regaffairs.dash') }}</td>
                <td class="num">{{ r.reportedAt || $t('regaffairs.dash') }}</td>
                <td>
                  <span class="st" :class="incidentStCls(r.status)"><span class="d"></span>{{ incidentStLabel(r.status) }}</span>
                </td>
              </tr>
              <tr v-if="!majors.length">
                <td colspan="6" class="emptyrow">{{ majorsError || $t('regaffairs.emptyRow') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { canWrite } from '@/auth.js'

const { t } = useI18n()

// ---- 顶部子公司分段（默认「集团」高亮，纯展示交互；后端隔离由 X-User 头驱动）----
const segs = ['all', 'pay', 'consumer']
const activeSeg = ref(0)

// ---- Tab 切换（顺序照搬原型 tabbar）----
// 报送日历 / 年度合规计划 / 监管问询 / 处罚与约谈 / 重大事件报送
const tabs = ['calendar', 'plan', 'inquiry', 'penalty', 'major']
const activeTab = ref('calendar')

// =============================================================
// 真调后端（DM-5：前端不得用静态假数据；字段以后端为准）
// 各台账独立持有列表与错误态；onMounted 并发拉取。
//  - 报送日历   GET /api/reg-filings
//  - 监管问询   GET /api/reg-inquiries
//  - 处罚与约谈 GET /api/reg-penalties
//  - 重大事件   GET /api/major-incidents
// 年度合规计划：后端无对应实体（DM-5 E 类）→ 模板占位，不拉数据。
// =============================================================
const filings = ref([])
const filingsError = ref('')
const inquiries = ref([])
const inquiriesError = ref('')
const penalties = ref([])
const penaltiesError = ref('')
const majors = ref([])
const majorsError = ref('')

onMounted(() => {
  api.get('/reg-filings').then((d) => { filings.value = d || [] }).catch((e) => { filingsError.value = e.message })
  api.get('/reg-inquiries').then((d) => { inquiries.value = d || [] }).catch((e) => { inquiriesError.value = e.message })
  api.get('/reg-penalties').then((d) => { penalties.value = d || [] }).catch((e) => { penaltiesError.value = e.message })
  api.get('/major-incidents').then((d) => { majors.value = d || [] }).catch((e) => { majorsError.value = e.message })
})

// ---- 登记监管报送：弹窗 → POST /api/reg-filings → 刷新报送日历（端到端写）----
const showCreate = ref(false)
const saving = ref(false)
const createError = ref('')
const form = reactive({ title: '', regulator: '', statutoryDeadline: '', orgId: 12 })
function openCreate() {
  form.title = ''
  form.regulator = ''
  form.statutoryDeadline = ''
  form.orgId = 12
  createError.value = ''
  showCreate.value = true
}
async function submitCreate() {
  saving.value = true
  createError.value = ''
  try {
    await api.post('/reg-filings', {
      orgId: form.orgId,
      title: form.title,
      regulator: form.regulator || null,
      statutoryDeadline: form.statutoryDeadline
    })
    filings.value = await api.get('/reg-filings') // 创建后刷新报送日历
    showCreate.value = false
  } catch (e) {
    createError.value = e.message
  } finally {
    saving.value = false
  }
}

// ---- 报送审批两步化：准备/提交评审/审批通过/驳回/关闭 → 刷新报送日历 ----
const filingBusy = ref(null)
async function filingAct(r, op) {
  filingBusy.value = r.id
  try {
    await api.post('/reg-filings/' + r.id + '/' + op, {})
    filings.value = await api.get('/reg-filings')
  } catch (e) {
    filingsError.value = e.message
  } finally {
    filingBusy.value = null
  }
}

// ---- KPI 卡：能派生的取真实计数，派生不了在模板里显「—」----
// 待报送数 = reg-filings 中 status∈{TO_DRAFT,DRAFTING}
const kpiDueToSubmit = computed(() =>
  filings.value.filter((r) => r.status === 'TO_DRAFT' || r.status === 'DRAFTING').length
)
// 未结问询 = reg-inquiries 中 status≠CLOSED
const kpiOpenInquiry = computed(() =>
  inquiries.value.filter((r) => r.status !== 'CLOSED').length
)
// 处罚整改未闭环 = reg-penalties 中 status≠CLOSED
const kpiPenaltyOpen = computed(() =>
  penalties.value.filter((r) => r.status !== 'CLOSED').length
)
// 重大事件报送 = major-incidents 总数
const kpiMajorCount = computed(() => majors.value.length)

// =============================================================
// 状态枚举 → 样式类 / i18n 标签（标签走 regaffairs.*Status 映射；
// 未知枚举值兜底 'wait' 与「—」，不臆造）
// =============================================================
// 报送日历 RegFilingStatus
const FILING_CLS = { TO_DRAFT: 'wait', DRAFTING: 'doing', SUBMITTED: 'ok', CLOSED: 'ok' }
const filingStCls = (s) => FILING_CLS[s] || 'wait'
const filingStLabel = (s) => (s ? t('regaffairs.filingStatus.' + s) : t('regaffairs.dash'))
// 监管问询 RegInquiryStatus
const INQUIRY_CLS = { DRAFTING: 'doing', REPLIED: 'ok', AWAIT_FEEDBACK: 'wait', CLOSED: 'ok' }
const inquiryStCls = (s) => INQUIRY_CLS[s] || 'wait'
const inquiryStLabel = (s) => (s ? t('regaffairs.inquiryStatus.' + s) : t('regaffairs.dash'))
// 处罚约谈 RegPenaltyStatus
const PENALTY_CLS = { OPEN: 'wait', RECTIFYING: 'doing', CLOSED: 'ok' }
const penaltyStCls = (s) => PENALTY_CLS[s] || 'wait'
const penaltyStLabel = (s) => (s ? t('regaffairs.penaltyStatus.' + s) : t('regaffairs.dash'))
// 重大事件 MajorIncidentStatus
const INCIDENT_CLS = { DRAFT: 'wait', REPORTED: 'doing', CLOSED: 'ok' }
const incidentStCls = (s) => INCIDENT_CLS[s] || 'wait'
const incidentStLabel = (s) => (s ? t('regaffairs.incidentStatus.' + s) : t('regaffairs.dash'))

// =============================================================
// Tab2 · 年度合规计划（M11，真实后端 /api/compliance-plans）
//   计划状态机 DRAFT→ACTIVE→CLOSED；计划项 PENDING→IN_PROGRESS→DONE。
// =============================================================
const plans = ref([])
const plansError = ref('')
const selPlanId = ref(null)
const planItems = ref([])
const planBusy = ref(null)   // 正在流转的计划 id
const itemBusy = ref(null)   // 正在改状态的事项 id
const planOpError = ref('')
const PLAN_CLS = { DRAFT: 'wait', ACTIVE: 'doing', CLOSED: 'ok' }

async function loadPlans() {
  plansError.value = ''
  try { plans.value = await api.get('/compliance-plans') } catch (e) { plansError.value = e.message; plans.value = [] }
}
async function selectPlan(p) {
  selPlanId.value = p.id
  try { planItems.value = await api.get('/compliance-plans/' + p.id + '/items') } catch (e) { planItems.value = [] }
}
// 计划状态机流转：activate/close
async function planAct(p, op) {
  planBusy.value = p.id; planOpError.value = ''
  try { await api.post('/compliance-plans/' + p.id + '/' + op, {}); await loadPlans() }
  catch (e) { planOpError.value = e.message } finally { planBusy.value = null }
}
// 计划项状态切换：POST /items/{itemId}/status
async function setItemStatus(it, status) {
  itemBusy.value = it.id; planOpError.value = ''
  try {
    await api.post('/compliance-plans/items/' + it.id + '/status', { status })
    if (selPlanId.value) await selectPlan({ id: selPlanId.value })
  } catch (e) { planOpError.value = e.message } finally { itemBusy.value = null }
}

// 登记计划 / 新增事项
const showPlanCreate = ref(false)
const showItemCreate = ref(false)
const planSaving = ref(false)
const planForm = reactive({ year: 2026, title: '', owner: '', orgId: 12 })
const itemForm = reactive({ matter: '', ownerDept: '', dueDate: '' })
function openPlanCreate() { Object.assign(planForm, { year: 2026, title: '', owner: '', orgId: 12 }); planOpError.value = ''; showPlanCreate.value = true }
function openItemCreate() { Object.assign(itemForm, { matter: '', ownerDept: '', dueDate: '' }); planOpError.value = ''; showItemCreate.value = true }
async function submitPlan() {
  planSaving.value = true; planOpError.value = ''
  try {
    await api.post('/compliance-plans', { orgId: planForm.orgId, year: planForm.year, title: planForm.title, owner: planForm.owner })
    showPlanCreate.value = false; await loadPlans()
  } catch (e) { planOpError.value = e.message } finally { planSaving.value = false }
}
async function submitItem() {
  planSaving.value = true; planOpError.value = ''
  try {
    await api.post('/compliance-plans/' + selPlanId.value + '/items', { matter: itemForm.matter, ownerDept: itemForm.ownerDept, dueDate: itemForm.dueDate || null })
    showItemCreate.value = false; await selectPlan({ id: selPlanId.value })
  } catch (e) { planOpError.value = e.message } finally { planSaving.value = false }
}

// 年度计划数据在挂载时一并拉取
onMounted(loadPlans)
</script>

<style scoped>
/* ========================================================
   样式严格对齐原型 #view-regaffairs 区块及其依赖的全局 CSS。
   颜色一律走 tokens.css 语义令牌。
   主题表头底色规则用更具体的类名 .view-regaffairs，避免污染其它页。
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

/* ---- KPI 卡片 kpibar（k5 / k4）---- */
.kpibar {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 11px;
  margin-bottom: 14px;
}
.kpibar.k5 {
  grid-template-columns: repeat(5, 1fr);
}
.kpibar.k4 {
  grid-template-columns: repeat(4, 1fr);
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
.kc .s {
  font-size: 10.5px;
  color: var(--text-3);
  margin-top: 4px;
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
.ch .more {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--accent-strong);
  cursor: pointer;
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
.seg2.g {
  background: var(--success);
}

/* ---- 年度合规计划 Tab + 报送审批两步化 专用 ---- */
.btn.sm {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
}
.btn.ghost.danger {
  color: var(--danger);
  border-color: var(--danger);
}
.muted {
  color: var(--text-3);
  font-size: 11.5px;
}
tbody tr.clk {
  cursor: pointer;
}
tbody tr.on {
  background: var(--accent-tint);
}
td.ops {
  display: flex;
  gap: 8px;
  align-items: center;
}
.hint {
  color: var(--text-3);
  font-size: 12.5px;
  padding: 22px 18px;
  text-align: center;
}
.mini-sel {
  height: 26px;
  padding: 0 6px;
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-sm);
  background: var(--bg);
  color: var(--text-1);
  font-size: 11.5px;
  font-family: inherit;
  outline: none;
  cursor: pointer;
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

/* ---- 提示信息行 infoline ---- */
.infoline {
  display: flex;
  align-items: center;
  gap: 9px;
  background: var(--accent-tint);
  border: 1px solid var(--accent-weak);
  border-left: 3px solid var(--accent);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: 12px;
  color: var(--accent-strong);
  margin-bottom: 14px;
}
.infoline svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

/* ---- 空态 empty ---- */
.empty {
  text-align: center;
  padding: 36px 16px;
  color: var(--text-3);
}
.empty .t {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-2);
}
.empty .d {
  font-size: 11.5px;
  margin-top: 5px;
}

/* ---- 进度条 prog（年度合规计划进度）---- */
.prog {
  height: 6px;
  width: 80px;
  background: rgba(120, 120, 120, 0.13);
  border-radius: 4px;
  overflow: hidden;
  display: inline-block;
  vertical-align: middle;
  margin-right: 7px;
}
.prog i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--accent-bright), var(--accent));
  border-radius: 4px;
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

/* ---- 空数据行（后端真实返回空 / 拉取出错）---- */
.emptyrow {
  color: var(--text-3);
  text-align: center;
  padding: 18px;
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

/* ---- 类型 / 类别标识 pill ---- */
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

/* ---- 处罚/约谈类型标签 tag ---- */
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

/* ---- 朱砂 t-gov 表头底色（用更具体的 .view-regaffairs 限定，避免污染其它页）---- */
:global(body.t-gov .view-regaffairs thead th) {
  background: var(--accent-tint);
}

/* ---- 登记弹窗（登记监管报送）---- */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.32);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}
.modal-card {
  width: 420px;
  max-width: 92vw;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-2);
  padding: 22px 24px;
}
.modal-card h3 {
  margin: 0 0 16px;
  font-size: 16px;
  color: var(--text-1);
}
.modal-card .fld {
  display: block;
  font-size: 12.5px;
  color: var(--text-2);
  margin-bottom: 12px;
}
.modal-card .fld input,
.modal-card .fld select {
  display: block;
  width: 100%;
  height: 38px;
  margin-top: 5px;
  padding: 0 11px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
  color: var(--text-1);
  font-size: 13.5px;
  font-family: inherit;
  outline: none;
}
.modal-card .cerr {
  color: var(--danger);
  font-size: 12.5px;
  margin: 0 0 12px;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}
.btn.ghost {
  background: var(--bg);
  color: var(--text-2);
  border: 1px solid var(--border);
}
.btn[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
