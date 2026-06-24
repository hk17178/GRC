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
        <button class="btn">{{ $t('regaffairs.register') }}</button>
      </div>

      <!-- ===== KPI 五卡 ===== -->
      <div class="kpibar k5">
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.dueMonth') }}</div>
          <div class="v" style="color: var(--accent)">6</div>
          <div class="s">{{ $t('regaffairs.kpi.dueMonthSub') }}</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.overdue') }}</div>
          <div class="v" style="color: var(--danger)">0</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.openInquiry') }}</div>
          <div class="v" style="color: var(--warning)">3</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.penaltyOpen') }}</div>
          <div class="v" style="color: var(--danger)">1</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('regaffairs.kpi.majorReport') }}</div>
          <div class="v">0</div>
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
          <!-- 左：监管报送日历表 -->
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
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in calendarRows" :key="r.item">
                  <td>{{ $t(r.item) }}</td>
                  <td>{{ $t(r.regulator) }}</td>
                  <td><span class="pill" :class="r.typePill">{{ $t(r.type) }}</span></td>
                  <td class="num" :style="r.deadlineStyle">{{ r.deadlineKey ? $t(r.deadlineKey) : r.deadline }}</td>
                  <td><span class="av-s">{{ r.avatar }}</span>{{ $t(r.owner) }}</td>
                  <td>
                    <span class="st" :class="r.stClass"><span class="d"></span>{{ $t(r.stLabel) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 右：报送达成 + 临近报送 -->
          <div>
            <!-- 报送达成（本年）bars -->
            <div class="card">
              <div class="ch"><h3>{{ $t('regaffairs.achieve.title') }}</h3></div>
              <div class="cb">
                <div class="bars">
                  <div v-for="b in achieveBars" :key="b.label" class="bar-row">
                    <div class="hd"><span class="nm">{{ $t(b.label) }}</span><b>{{ b.v }}</b></div>
                    <div class="track">
                      <div class="seg2 g" :style="{ width: b.w }"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 临近报送 srow -->
            <div class="card">
              <div class="ch"><h3>{{ $t('regaffairs.upcoming.title') }}</h3></div>
              <div class="cb">
                <div class="srow">
                  <span>{{ $t('regaffairs.calendar.itemPbocStat') }}</span>
                  <b style="color: var(--danger)">{{ $t('regaffairs.upcoming.d2') }}</b>
                </div>
                <div class="srow">
                  <span>{{ $t('regaffairs.calendar.itemAml') }}</span>
                  <b style="color: var(--warning)">{{ $t('regaffairs.upcoming.d7') }}</b>
                </div>
                <div class="srow">
                  <span>{{ $t('regaffairs.calendar.itemReserve') }}</span>
                  <b>{{ $t('regaffairs.upcoming.d12') }}</b>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab2 · 年度合规计划 ========== -->
      <div v-show="activeTab === 'plan'" class="tabpane">
        <!-- KPI 四卡 -->
        <div class="kpibar k4">
          <div class="kc">
            <div class="l">{{ $t('regaffairs.plan.kpi.total') }}</div>
            <div class="v">38</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('regaffairs.plan.kpi.done') }}</div>
            <div class="v" style="color: var(--success)">19</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('regaffairs.plan.kpi.doing') }}</div>
            <div class="v">14</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('regaffairs.plan.kpi.overdue') }}</div>
            <div class="v" style="color: var(--danger)">2</div>
          </div>
        </div>

        <div class="card">
          <div class="ch">
            <h3>{{ $t('regaffairs.plan.title') }}</h3>
            <span class="more">{{ $t('regaffairs.plan.add') }}</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>{{ $t('regaffairs.plan.th.item') }}</th>
                <th>{{ $t('regaffairs.plan.th.category') }}</th>
                <th>{{ $t('regaffairs.plan.th.dept') }}</th>
                <th>{{ $t('regaffairs.plan.th.planDone') }}</th>
                <th>{{ $t('regaffairs.plan.th.progress') }}</th>
                <th>{{ $t('regaffairs.plan.th.status') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in planRows" :key="r.item">
                <td>{{ $t(r.item) }}</td>
                <td><span class="pill" :class="r.catPill">{{ $t(r.category) }}</span></td>
                <td>{{ $t(r.dept) }}</td>
                <td>{{ r.planDone }}</td>
                <td><span class="prog"><i :style="{ width: r.progress }"></i></span>{{ r.progress }}</td>
                <td>
                  <span class="st" :class="r.stClass"><span class="d"></span>{{ $t(r.stLabel) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
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
            <tbody>
              <tr v-for="r in inquiryRows" :key="r.id">
                <td class="code">{{ r.id }}</td>
                <td>{{ $t(r.regulator) }}</td>
                <td>{{ $t(r.subject) }}</td>
                <td class="num">{{ r.received }}</td>
                <td class="num" :style="r.dueStyle">{{ r.replyDue }}</td>
                <td>
                  <span v-if="r.replyLogLabel" class="st" :class="r.replyLogClass"><span class="d"></span>{{ $t(r.replyLogLabel) }}</span>
                  <span v-else>—</span>
                </td>
                <td>
                  <span class="st" :class="r.stClass"><span class="d"></span>{{ $t(r.stLabel) }}</span>
                </td>
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
            <tbody>
              <tr v-for="r in penaltyRows" :key="r.id">
                <td class="code">{{ r.id }}</td>
                <td><span class="tag" :class="r.typeClass">{{ $t(r.type) }}</span></td>
                <td>{{ $t(r.regulator) }}</td>
                <td>{{ $t(r.reason) }}</td>
                <td class="num">{{ r.date }}</td>
                <td>{{ $t(r.remediation) }}</td>
                <td>
                  <span class="st" :class="r.stClass"><span class="d"></span>{{ $t(r.stLabel) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ========== Tab5 · 重大事件报送 ========== -->
      <div v-show="activeTab === 'major'" class="tabpane">
        <!-- 提示信息行 infoline -->
        <div class="infoline">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9" /></svg>
          {{ $t('regaffairs.major.info') }}
        </div>
        <!-- 空态 -->
        <div class="card">
          <div class="empty">
            <div class="t">{{ $t('regaffairs.major.emptyTitle') }}</div>
            <div class="d">{{ $t('regaffairs.major.emptyDesc') }}</div>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref } from 'vue'
import AppShell from '@/components/AppShell.vue'

// ---- 顶部子公司分段（默认「集团」高亮，纯展示）----
// 原型为：集团 / 支付科技 / 消费金融
const segs = ['all', 'pay', 'consumer']
const activeSeg = ref(0)

// ---- Tab 切换（顺序照搬原型 tabbar）----
// 报送日历 / 年度合规计划 / 监管问询 / 处罚与约谈 / 重大事件报送
const tabs = ['calendar', 'plan', 'inquiry', 'penalty', 'major']
const activeTab = ref('calendar')

// ---- Tab1：监管报送日历表（静态示例值取自原型）----
// avatar 为原型中责任人头像首字（与语言无关，固定显示）
const calendarRows = [
  { item: 'regaffairs.calendar.itemPbocStat', regulator: 'regaffairs.calendar.regPboc', type: 'regaffairs.calendar.typeMonthly', typePill: '', deadlineKey: 'regaffairs.calendar.due0705', deadlineStyle: { color: 'var(--danger)' }, avatar: '陈', owner: 'regaffairs.calendar.ownerChen', stClass: 'doing', stLabel: 'regaffairs.calendar.stDrafting' },
  { item: 'regaffairs.calendar.itemAml', regulator: 'regaffairs.calendar.regAml', type: 'regaffairs.calendar.typeMonthly', typePill: '', deadline: '07-10', deadlineStyle: {}, avatar: '李', owner: 'regaffairs.calendar.ownerLi', stClass: 'wait', stLabel: 'regaffairs.calendar.stToDraft' },
  { item: 'regaffairs.calendar.itemPi', regulator: 'regaffairs.calendar.regCac', type: 'regaffairs.calendar.typeAnnual', typePill: 'blue', deadline: '08-31', deadlineStyle: {}, avatar: '王', owner: 'regaffairs.calendar.ownerWang', stClass: 'wait', stLabel: 'regaffairs.calendar.stToDraft' },
  { item: 'regaffairs.calendar.itemReserve', regulator: 'regaffairs.calendar.regPboc', type: 'regaffairs.calendar.typeQuarterly', typePill: '', deadline: '07-15', deadlineStyle: {}, avatar: '张', owner: 'regaffairs.calendar.ownerZhang', stClass: 'ok', stLabel: 'regaffairs.calendar.stSubmitted' }
]

// ---- Tab1 右栏：报送达成（本年）bars ----
const achieveBars = [
  { label: 'regaffairs.achieve.onTime', v: '100%', w: '100%' },
  { label: 'regaffairs.achieve.monthly', v: '6/6', w: '100%' },
  { label: 'regaffairs.achieve.quarterly', v: '2/2', w: '100%' }
]

// ---- Tab2：监管问询表 ----
const inquiryRows = [
  { id: 'RQ-2026-08', regulator: 'regaffairs.inquiry.regPboc', subject: 'regaffairs.inquiry.subjLargeTxn', received: '06-18', replyDue: '06-28', dueStyle: { color: 'var(--danger)' }, replyLogLabel: '', stClass: 'doing', stLabel: 'regaffairs.inquiry.stReplyDrafting' },
  { id: 'RQ-2026-07', regulator: 'regaffairs.inquiry.regCac', subject: 'regaffairs.inquiry.subjPiExport', received: '06-10', replyDue: '06-24', dueStyle: {}, replyLogClass: 'ok', replyLogLabel: 'regaffairs.inquiry.stReplied', stClass: 'wait', stLabel: 'regaffairs.inquiry.stAwaitFeedback' },
  { id: 'RQ-2026-05', regulator: 'regaffairs.inquiry.regNafr', subject: 'regaffairs.inquiry.subjOutsource', received: '05-28', replyDue: '06-11', dueStyle: {}, replyLogClass: 'ok', replyLogLabel: 'regaffairs.inquiry.stReplied', stClass: 'ok', stLabel: 'regaffairs.inquiry.stClosed' }
]

// ---- Tab3：处罚与约谈台账表 ----
// tag.m = 约谈（警示）；tag.h = 行政处罚（高）
const penaltyRows = [
  { id: 'RP-2026-02', type: 'regaffairs.penalty.typeTalk', typeClass: 'm', regulator: 'regaffairs.penalty.regPboc', reason: 'regaffairs.penalty.reasonKyc', date: '06-05', remediation: 'regaffairs.penalty.remed30d', stClass: 'doing', stLabel: 'regaffairs.penalty.stRemediating' },
  { id: 'RP-2025-11', type: 'regaffairs.penalty.typePenalty', typeClass: 'h', regulator: 'regaffairs.penalty.regLocalPboc', reason: 'regaffairs.penalty.reasonReserve', date: '2025-11', remediation: 'regaffairs.penalty.remedFine', stClass: 'ok', stLabel: 'regaffairs.penalty.stRepliedClosed' }
]

// ---- Tab5：年度合规计划表 ----
const planRows = [
  { item: 'regaffairs.plan.itemMlps', category: 'regaffairs.plan.catAssess', catPill: '', dept: 'regaffairs.plan.deptInfosec', planDone: 'Q4', progress: '70%', stClass: 'doing', stLabel: 'regaffairs.plan.stDoing' },
  { item: 'regaffairs.plan.itemPiTrain', category: 'regaffairs.plan.catTrain', catPill: 'blue', dept: 'regaffairs.plan.deptCompliance', planDone: 'Q3', progress: '40%', stClass: 'doing', stLabel: 'regaffairs.plan.stDoing' },
  { item: 'regaffairs.plan.itemAmlInspect', category: 'regaffairs.plan.catInspect', catPill: '', dept: 'regaffairs.plan.deptCompliance', planDone: 'Q2', progress: '100%', stClass: 'ok', stLabel: 'regaffairs.plan.stDone' },
  { item: 'regaffairs.plan.itemDataSelf', category: 'regaffairs.plan.catSelf', catPill: '', dept: 'regaffairs.plan.deptInfosec', planDone: 'Q2', progress: '20%', stClass: 'over', stLabel: 'regaffairs.plan.stOverdue' }
]
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
</style>
