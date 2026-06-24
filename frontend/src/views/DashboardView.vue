<!-- =============================================================
     合规态势驾驶舱主页（DashboardView）
     说明：严格按高保真原型「驾驶舱版.html」的 #view-dashboard 区域
     1:1 复原，逐区块还原其 DOM 结构与内联 CSS：
       1) 页头 phead（标题 + 子公司分段切换 + 编辑布局/添加组件按钮）；
       2) KPI 指标卡组 kpibar（8 张卡，含进度条）；
       3) 可编辑大屏栅格 dgrid（12 列 span）：
          · 子公司 × 风险域 · 热力矩阵（等宽列 heat，span 6）
          · 整改完成率 · 分子公司（bars，span 3）
          · KRI 持续监控（kri + 迷你折线，span 3）
          · 体系合规达成度（bars，span 4）
          · 待我审批（worklist，span 4）
          · 重点关注 · 实时事件流（feed，span 4）
     配色/间距/圆角/字号全部照搬原型；颜色复用 tokens.css 语义令牌，
     热力矩阵单元格按原型保留其内联十六进制底色（红→绿渐变示例值）。
     文案走 i18n（zh/en 同步），静态示例数值取自原型。
     ============================================================= -->
<template>
  <AppShell>
    <section class="view">
      <!-- ===== 页头：标题 + 子公司分段 + 操作按钮 ===== -->
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('dash.overviewTag') }}</div>
          <h1>
            {{ $t('dash.title') }}
            <small>{{ $t('dash.subtitle') }}</small>
          </h1>
        </div>
        <div class="sp"></div>
        <!-- 子公司分段切换（默认全集团高亮，纯展示） -->
        <div class="seg">
          <button
            v-for="(s, i) in segs"
            :key="s"
            :class="{ on: i === activeSeg }"
            @click="activeSeg = i"
          >
            {{ $t('dash.seg.' + s) }}
          </button>
        </div>
        <button class="btn ghost">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="14" width="7" height="7" rx="1" />
            <rect x="3" y="14" width="7" height="7" rx="1" />
          </svg>
          <span class="lbl">{{ $t('dash.editLayout') }}</span>
        </button>
        <button class="btn ghost">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14" />
          </svg>
          {{ $t('dash.addWidget') }}
        </button>
      </div>

      <!-- ===== KPI 指标卡组（8 张） ===== -->
      <div class="kpibar">
        <div class="kc" v-for="(k, i) in kpis" :key="i">
          <div class="l">{{ $t('dash.kpi.' + k.key + '.l') }}</div>
          <div class="v" :style="k.vColor ? { color: k.vColor } : null">
            {{ k.v }}
            <small v-if="k.suffix">{{ k.suffix }}</small>
            <span v-if="k.delta" :class="k.deltaDir">{{ k.delta }}</span>
          </div>
          <div class="s">{{ $t('dash.kpi.' + k.key + '.s') }}</div>
          <div class="pb">
            <i :style="{ width: k.pct + '%', background: k.bar }"></i>
          </div>
        </div>
      </div>

      <!-- 诚实标注：上方 KPI 卡为真实后端汇总；下方热力矩阵/体系达成度为原型视觉示意（后端暂无该聚合）。 -->
      <div class="dash-note">{{ $t('dash.scaffoldNote') }}</div>

      <!-- ===== 可编辑大屏栅格（12 列） ===== -->
      <div class="dgrid">
        <!-- 子公司 × 风险域 · 热力矩阵（span 6 · 等宽列） -->
        <div class="card gi" style="--w: 6">
          <div class="ch">
            <h3>{{ $t('dash.heat.title') }}</h3>
            <span class="sub">{{ $t('dash.heat.sub') }}</span>
          </div>
          <div class="cb">
            <table class="heat">
              <thead>
                <tr>
                  <th></th>
                  <th v-for="d in heatDomains" :key="d">{{ $t('dash.heat.domain.' + d) }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in heatRows" :key="row.sub">
                  <td class="lbl">{{ $t('dash.sub.' + row.sub) }}</td>
                  <td
                    v-for="(cell, ci) in row.cells"
                    :key="ci"
                    class="cell"
                    :style="{ background: cell.c }"
                  >
                    {{ cell.v }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 整改完成率 · 分子公司（span 3） -->
        <div class="card gi" style="--w: 3">
          <div class="ch">
            <h3>{{ $t('dash.remed.title') }}</h3>
          </div>
          <div class="cb">
            <div class="bars">
              <div class="bar-row" v-for="r in remediation" :key="r.sub">
                <div class="hd">
                  <span class="nm">{{ $t('dash.sub.' + r.sub) }}</span>
                  <span>
                    <b>{{ r.pct }}%</b>
                    <span v-if="r.overdue" class="ov">{{ $t('dash.overdue', { n: r.overdue }) }}</span>
                  </span>
                </div>
                <div class="track">
                  <div class="seg2" :class="r.tone" :style="{ width: r.pct + '%' }"></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- KRI 持续监控（span 3 · 含迷你折线） -->
        <div class="card gi" style="--w: 3">
          <div class="ch">
            <h3>{{ $t('dash.kri.title') }}</h3>
            <span class="sub">{{ $t('dash.kri.sub') }}</span>
          </div>
          <div class="cb">
            <div class="kri">
              <div class="ki" v-for="(k, i) in kris" :key="i">
                <span class="dt" :style="{ background: k.dot }"></span>
                <span class="nm">
                  <div class="t">{{ $t('dash.kri.item.' + k.key + '.t') }}</div>
                  <div class="src">{{ $t('dash.kri.item.' + k.key + '.src') }}</div>
                </span>
                <svg class="kspark" viewBox="0 0 54 18" preserveAspectRatio="none">
                  <polyline :points="k.spark" fill="none" :stroke="k.line" stroke-width="1.5" />
                </svg>
                <span class="val" :style="k.valColor ? { color: k.valColor } : null">
                  {{ k.val }}
                  <div class="th">{{ $t('dash.kri.item.' + k.key + '.th') }}</div>
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 体系合规达成度（span 4） -->
        <div class="card gi" style="--w: 4">
          <div class="ch">
            <h3>{{ $t('dash.frame.title') }}</h3>
            <span class="sub">{{ $t('dash.frame.sub') }}</span>
          </div>
          <div class="cb">
            <div class="bars">
              <div class="bar-row" v-for="f in frameworks" :key="f.name">
                <div class="hd">
                  <span class="nm">{{ f.name }}</span>
                  <b>{{ f.pct }}%</b>
                </div>
                <div class="track">
                  <div class="seg2" :class="f.tone" :style="{ width: f.pct + '%' }"></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 待我审批（span 4） -->
        <div class="card gi" style="--w: 4">
          <div class="ch">
            <h3>{{ $t('dash.approve.title') }}</h3>
            <span class="more">{{ $t('dash.approve.all', { n: approvals.length }) }}</span>
          </div>
          <div class="cb">
            <div class="wl">
              <div class="wi" v-for="(a, i) in approvals" :key="i">
                <span class="tp2">{{ $t('dash.approve.type.' + a.type) }}</span>
                <div class="ti">
                  <div class="t">{{ $t('dash.approve.item.' + a.key + '.t') }}</div>
                  <div class="m">{{ $t('dash.approve.item.' + a.key + '.m') }}</div>
                </div>
                <span class="due" :class="{ ov: a.overdue }">
                  {{ a.overdue ? $t('dash.due.overdue', { v: a.dueVal }) : $t('dash.due.pending', { v: a.dueVal }) }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 重点关注 · 实时事件流（span 4） -->
        <div class="card gi" style="--w: 4; padding: 0">
          <div class="ch" style="padding: 14px 18px 8px">
            <h3>{{ $t('dash.feed.title') }}</h3>
          </div>
          <div class="feed">
            <div class="it" v-for="(f, i) in feed" :key="i">
              <span class="tm">{{ f.tmIsTime ? f.tm : $t('dash.feed.' + f.tm) }}</span>
              <span class="bd" :class="f.badge">{{ $t('dash.feed.badge.' + f.badge) }}</span>
              <span class="tx">{{ $t('dash.feed.item.' + f.key) }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
// 驾驶舱主页：视觉/样式 1:1 取自原型；KPI 指标卡接真实后端 /api/dashboard/summary（按域汇总）。
// 热力矩阵 / 体系达成度等分析图为原型视觉示意（后端暂无该聚合，标注示意，不臆造）。
import { ref, computed, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'

// ---- 子公司分段切换（页头右侧 seg）----
const segs = ['all', 'pay', 'consumer', 'tech']
const activeSeg = ref(0)

// ---- 合规态势汇总（真实后端：跨模块按可见组织计数）----
const summary = ref(null)
const loadError = ref('')
onMounted(async () => {
  try {
    summary.value = await api.get('/dashboard/summary')
  } catch (e) {
    loadError.value = e.message
  }
})
const dv = (x) => (summary.value ? x : '—')        // 未加载到则诚实显「—」
const pct = (x) => (x == null ? 0 : Math.min(x * 12, 100)) // 进度条宽度（装饰，按计数缩放封顶）

// ---- KPI 指标卡组（8 张，保留原型卡片视觉；数值全部接真实汇总）----
const kpis = computed(() => {
  const r = summary.value?.risk || {}
  const a = summary.value?.audit || {}
  const g = summary.value?.regulatory || {}
  const p = summary.value?.policy || {}
  const m = summary.value?.permission || {}
  return [
    { key: 'openRisk', v: dv(r.openFindings), bar: 'var(--accent)', pct: pct(r.openFindings) },
    { key: 'gated', v: dv(r.gatedFindings), vColor: 'var(--danger)', bar: 'var(--danger)', pct: pct(r.gatedFindings) },
    { key: 'kriWarn', v: dv(r.kriWarning), vColor: 'var(--warning)', bar: 'var(--warning)', pct: pct(r.kriWarning) },
    { key: 'kriCrit', v: dv(r.kriCritical), vColor: 'var(--danger)', bar: 'var(--danger)', pct: pct(r.kriCritical) },
    { key: 'openAudit', v: dv(a.openFindings), bar: 'var(--accent)', pct: pct(a.openFindings) },
    { key: 'pendingFiling', v: dv(g.pendingFilings), vColor: 'var(--warning)', bar: 'var(--warning)', pct: pct(g.pendingFilings) },
    { key: 'effPolicy', v: dv(p.effective), vColor: 'var(--info)', bar: 'var(--info)', pct: pct(p.effective) },
    { key: 'pendingSod', v: dv(m.pendingSodExceptions), vColor: 'var(--warning)', bar: 'var(--warning)', pct: pct(m.pendingSodExceptions) }
  ]
})

// ---- 热力矩阵：风险域列（等宽）与子公司行 ----
const heatDomains = ['infosec', 'data', 'continuity', 'thirdparty', 'reg', 'control']
// cells 的底色保留原型内联十六进制（红→绿综合风险值示例，0–100）
const heatRows = [
  { sub: 'hq', cells: [
    { v: 41, c: '#dfb84d' }, { v: 33, c: '#9cbf6e' }, { v: 28, c: '#7fa76a' },
    { v: 52, c: '#e0a93f' }, { v: 38, c: '#cdbf57' }, { v: 30, c: '#86ab69' } ] },
  { sub: 'pay', cells: [
    { v: 78, c: '#c0392b' }, { v: 71, c: '#cf6233' }, { v: 55, c: '#e0a93f' },
    { v: 68, c: '#d4853a' }, { v: 82, c: '#c0392b' }, { v: 49, c: '#dba148' } ] },
  { sub: 'consumer', cells: [
    { v: 64, c: '#d99845' }, { v: 58, c: '#e0a93f' }, { v: 47, c: '#dba148' },
    { v: 73, c: '#cf6233' }, { v: 66, c: '#d68a3c' }, { v: 44, c: '#d6aa49' } ] },
  { sub: 'wealth', cells: [
    { v: 52, c: '#e0a93f' }, { v: 61, c: '#d99845' }, { v: 39, c: '#bcbf5c' },
    { v: 57, c: '#e0a93f' }, { v: 54, c: '#dd9a44' }, { v: 36, c: '#a8b566' } ] },
  { sub: 'tech', cells: [
    { v: 69, c: '#d68a3c' }, { v: 47, c: '#dba148' }, { v: 51, c: '#dba148' },
    { v: 60, c: '#d99845' }, { v: 43, c: '#cdbf57' }, { v: 55, c: '#e0a93f' } ] },
  { sub: 'factoring', cells: [
    { v: 48, c: '#dba148' }, { v: 44, c: '#d6aa49' }, { v: 62, c: '#d99845' },
    { v: 51, c: '#e0a93f' }, { v: 49, c: '#dba148' }, { v: 41, c: '#cdbf57' } ] }
]

// ---- 整改完成率 · 分子公司 ----
const remediation = [
  { sub: 'hq', pct: 88, overdue: 1, tone: 'g' },
  { sub: 'pay', pct: 64, overdue: 7, tone: 'h' },
  { sub: 'consumer', pct: 73, overdue: 3, tone: 'm' },
  { sub: 'wealth', pct: 85, overdue: 1, tone: 'g' },
  { sub: 'tech', pct: 91, overdue: 0, tone: 'g' },
  { sub: 'factoring', pct: 82, overdue: 0, tone: 'm' }
]

// ---- KRI 持续监控（含迷你折线 points 与状态色）----
const kris = [
  { key: 'vulnFix', dot: 'var(--danger)', line: 'var(--danger)', valColor: 'var(--danger)', val: '23.4', spark: '0,14 11,12 22,13 33,8 44,6 54,3' },
  { key: 'privAcct', dot: 'var(--danger)', line: 'var(--danger)', valColor: 'var(--danger)', val: '7', spark: '0,15 11,13 22,14 33,10 44,7 54,5' },
  { key: 'logRetain', dot: 'var(--success)', line: 'var(--success)', val: '162', spark: '0,9 11,10 22,8 33,9 44,8 54,8' },
  { key: 'apiErr', dot: 'var(--warning)', line: 'var(--warning)', valColor: 'var(--warning)', val: '0.71%', spark: '0,12 11,9 22,13 33,8 44,11 54,7' },
  { key: 'exportApprove', dot: 'var(--success)', line: 'var(--success)', val: '99.6%', spark: '0,5 11,6 22,4 33,5 44,4 54,3' }
]

// ---- 体系合规达成度（控制点覆盖率，标准名不翻译）----
const frameworks = [
  { name: 'ISO 27001', pct: 92, tone: 'g' },
  { name: '等保三级', pct: 86, tone: 'a' },
  { name: 'PBOC 支付监管', pct: 78, tone: 'm' },
  { name: 'PIPL 个人信息', pct: 81, tone: 'm' },
  { name: 'PCI DSS', pct: 74, tone: 'h' }
]

// ---- 待我审批 ----
const approvals = [
  { key: 'mlps', type: 'report', dueVal: '2h' },
  { key: 'consumerForm', type: 'assess', dueVal: '6h' },
  { key: 'dataExit', type: 'policy', dueVal: '1d', overdue: true },
  { key: 'reassess', type: 'reassess', dueVal: '3h' }
]

// ---- 重点关注 · 实时事件流 ----
// tmIsTime=true 时直接显示时间字符串，否则按 i18n（昨日）翻译
const feed = [
  { tm: '08:52', tmIsTime: true, badge: 'over', key: 'overdue' },
  { tm: '08:31', tmIsTime: true, badge: 'kri', key: 'kri' },
  { tm: '07:40', tmIsTime: true, badge: 'law', key: 'law' },
  { tm: 'yesterday', tmIsTime: false, badge: 'aud', key: 'audit' },
  { tm: 'yesterday', tmIsTime: false, badge: 'rev', key: 'review' }
]
</script>

<style scoped>
/* ========================================================
   样式严格对齐原型 #view-dashboard 区块的内联 CSS。
   颜色一律走 tokens.css 语义令牌（热力单元格底色除外，按原型保留内联色）。
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
.phead h1 small {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-3);
  margin-left: 8px;
  font-family: var(--font-sans);
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
.btn.ghost {
  background: var(--surface);
  color: var(--text-2);
  border: 1px solid var(--surface-border);
}

/* ---- KPI 指标卡组 ---- */
.kpibar {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 11px;
  margin-bottom: 14px;
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
.kc .v small {
  font-size: 12px;
  color: var(--text-3);
}
.kc .v .up {
  font-size: 11px;
  color: var(--success);
  font-weight: 700;
}
.kc .v .dn {
  font-size: 11px;
  color: var(--danger);
  font-weight: 700;
}
.kc .s {
  font-size: 10.5px;
  color: var(--text-3);
  margin-top: 5px;
}
.kc .pb {
  height: 4px;
  background: rgba(120, 120, 120, 0.13);
  border-radius: 3px;
  margin-top: 9px;
  overflow: hidden;
}
.kc .pb i {
  display: block;
  height: 100%;
  border-radius: 3px;
}

/* ---- 可编辑大屏栅格（12 列 span） ---- */
.dgrid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 14px;
  margin-bottom: 14px;
  grid-auto-flow: row dense;
}
.dgrid .gi {
  grid-column: span var(--w, 6);
  min-width: 0;
  margin-bottom: 0;
  position: relative;
}
@media (max-width: 1180px) {
  .dgrid .gi {
    grid-column: span 6 !important;
  }
}
@media (max-width: 820px) {
  .dgrid .gi {
    grid-column: span 12 !important;
  }
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
  font-weight: 600;
  cursor: pointer;
}
.cb {
  padding: 14px 18px 18px;
}

/* ---- 热力矩阵 heat（等宽列） ---- */
.heat {
  width: 100%;
  border-collapse: separate;
  border-spacing: 3px;
  table-layout: fixed;
}
.heat th:first-child,
.heat td.lbl {
  width: 96px;
}
.heat th {
  font-size: 10.5px;
  color: var(--text-3);
  font-weight: 600;
  padding: 3px 0;
  text-align: center;
}
.heat td.lbl {
  font-size: 11.5px;
  color: var(--text-2);
  text-align: right;
  padding-right: 8px;
  white-space: nowrap;
}
.heat td.cell {
  text-align: center;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 700;
  padding: 8px 0;
  color: #fff;
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
.bar-row .hd .ov {
  color: var(--danger);
  font-size: 11px;
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
.seg2.g {
  background: var(--success);
}

/* ---- KRI 监控列表 ---- */
.kri .ki {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-subtle);
}
.kri .ki:last-child {
  border: 0;
}
.kri .ki .dt {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.kri .ki .nm {
  flex: 1;
}
.kri .ki .nm .t {
  font-size: 12.5px;
}
.kri .ki .nm .src {
  font-size: 10.5px;
  color: var(--text-3);
  margin-top: 1px;
}
.kri .ki .val {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.kri .ki .val .th {
  font-size: 10.5px;
  color: var(--text-3);
  font-weight: 400;
}
.kspark {
  width: 54px;
  height: 18px;
  flex-shrink: 0;
}

/* ---- 待我审批 worklist ---- */
.wl .wi {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 11px 0;
  border-bottom: 1px solid var(--border-subtle);
}
.wl .wi:last-child {
  border: 0;
}
.wl .tp2 {
  font-size: 10px;
  font-weight: 700;
  padding: 3px 7px;
  border-radius: 5px;
  background: var(--accent-weak);
  color: var(--accent-strong);
  flex-shrink: 0;
  white-space: nowrap;
}
.wl .ti {
  flex: 1;
  min-width: 0;
}
.wl .ti .t {
  font-size: 12.5px;
}
.wl .ti .m {
  font-size: 10.5px;
  color: var(--text-3);
  margin-top: 2px;
}
.wl .due {
  font-size: 11px;
  color: var(--text-3);
  white-space: nowrap;
}
.wl .due.ov {
  color: var(--danger);
  font-weight: 600;
}

/* ---- 实时事件流 feed ---- */
.feed .it {
  display: flex;
  gap: 11px;
  padding: 11px 18px;
  border-top: 1px solid var(--border-subtle);
}
.feed .it:first-child {
  border-top: 0;
}
.feed .tm {
  font-size: 10.5px;
  color: var(--text-3);
  width: 42px;
  flex-shrink: 0;
  padding-top: 2px;
  font-variant-numeric: tabular-nums;
}
.feed .bd {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
  height: 18px;
  display: inline-flex;
  align-items: center;
}
.bd.law {
  background: var(--accent-weak);
  color: var(--accent-strong);
}
.bd.over {
  background: var(--danger-tint);
  color: var(--danger);
}
.bd.kri {
  background: var(--plum-tint);
  color: var(--plum);
}
.bd.aud {
  background: var(--warning-tint);
  color: #a87d22;
}
.bd.rev {
  background: var(--info-tint);
  color: var(--info);
}
.feed .tx {
  font-size: 12px;
  line-height: 1.45;
}
/* 诚实标注条：区分真实 KPI 与原型示意分析图 */
.dash-note {
  margin: 0 0 14px;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-2);
  background: var(--info-tint, rgba(40, 90, 150, 0.08));
  border: 1px solid var(--surface-border);
  border-left: 3px solid var(--info, #3a6ea5);
  border-radius: var(--radius-md);
}
</style>
