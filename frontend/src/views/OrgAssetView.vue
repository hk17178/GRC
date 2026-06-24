<!-- =============================================================
     组织与资产页（OrgAssetView · M6）
     说明：严格按高保真原型「驾驶舱版.html」的 #view-org 区域 1:1 复原，
     逐区块还原其 DOM 结构与内联 CSS：
       页头 phead（M6 标识 + 标题「组织架构与资产台账」+ AD 同步 ghost 按钮
         + ＋ 登记资产 主按钮）；
       Tab 切换 tabbar（组织架构 / 资产台账 / 个人信息处理活动(ROPA)，共 3 个）；
       Tab1 组织架构：左右栅格 g-main-side
         · 左：组织树 card（tree / tn / dot2 / cnt，含 lv2、lv3 缩进层级）
         · 右：AD 同步态势 sidecard（srow 列表 + 正常状态 st.ok）
       Tab2 资产台账：
         · KPI 四卡 kpibar.k4（资产总数 / 信息系统 / 高重要性 / 评估覆盖率）
         · 左右栅格 g-16-1
           · 左：资产台账表（含数据/合规属性列：数据分级 / 个人信息 / 跨境
                 / 等保定级 / 持卡人数据 / 重要性，横向滚动 min-width:760px）
           · 右：资产类型分布 bars（seg2.a 强调色）
       Tab3 个人信息处理活动(ROPA)：ROPA 表（PIPL 法定台账，
             横向滚动 min-width:860px）
     配色/间距/圆角/字号全部照搬原型；颜色复用 tokens.css 语义令牌。
     文案走 i18n（zh/en 同步），静态示例数值取自原型。
     主题表头底色规则用更具体的 .view-orgasset 限定，避免污染其它页。
     ============================================================= -->
<template>
  <AppShell>
    <section class="view view-orgasset">
      <!-- ===== 页头：M6 标识 + 标题 + AD 同步 + 登记资产 ===== -->
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('orgasset.tag') }}</div>
          <h1>{{ $t('orgasset.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn ghost">{{ $t('orgasset.adSync') }}</button>
        <button class="btn">{{ $t('orgasset.register') }}</button>
      </div>

      <!-- ===== Tab 切换 ===== -->
      <div class="tabbar">
        <button
          v-for="t in tabs"
          :key="t"
          :class="{ on: t === activeTab }"
          @click="activeTab = t"
        >
          {{ $t('orgasset.tab.' + t) }}
        </button>
      </div>

      <!-- ========== Tab1 · 组织架构 ========== -->
      <div v-show="activeTab === 'org'" class="tabpane">
        <div class="g g-main-side">
          <!-- 左：组织树（AD 同步）-->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('orgasset.tree.title') }}</h3>
              <span class="more">{{ $t('orgasset.tree.manualSync') }}</span>
            </div>
            <div class="cb">
              <div class="tree">
                <div
                  v-for="n in treeNodes"
                  :key="n.label"
                  class="tn"
                  :class="n.lv"
                >
                  <span class="dot2"></span>{{ $t(n.label) }}
                  <span v-if="n.cnt" class="cnt">{{ $t(n.cnt) }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 右：AD 同步态势侧卡 -->
          <div class="sidecard">
            <h4>{{ $t('orgasset.adStatus.title') }}</h4>
            <div class="srow">
              <span>{{ $t('orgasset.adStatus.lastSync') }}</span>
              <b>{{ $t('orgasset.adStatus.lastSyncVal') }}</b>
            </div>
            <div class="srow">
              <span>{{ $t('orgasset.adStatus.users') }}</span>
              <b>983</b>
            </div>
            <div class="srow">
              <span>{{ $t('orgasset.adStatus.disabled') }}</span>
              <b style="color: var(--danger)">12</b>
            </div>
            <div class="srow">
              <span>{{ $t('orgasset.adStatus.deprovision') }}</span>
              <b>{{ $t('orgasset.adStatus.deprovisionVal') }}</b>
            </div>
            <div class="srow">
              <span>{{ $t('orgasset.adStatus.status') }}</span>
              <span class="st ok"><span class="d"></span>{{ $t('orgasset.adStatus.statusVal') }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab2 · 资产台账 ========== -->
      <div v-show="activeTab === 'asset'" class="tabpane">
        <!-- KPI 四卡 -->
        <div class="kpibar k4">
          <div class="kc">
            <div class="l">{{ $t('orgasset.kpi.total') }}</div>
            <div class="v">186</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('orgasset.kpi.systems') }}</div>
            <div class="v">94</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('orgasset.kpi.highCrit') }}</div>
            <div class="v" style="color: var(--danger)">38</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('orgasset.kpi.coverage') }}</div>
            <div class="v" style="color: var(--success)">86<small>%</small></div>
          </div>
        </div>

        <div class="g g-16-1">
          <!-- 左：资产台账表（含数据/合规属性列，横向滚动）-->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('orgasset.asset.title') }}</h3>
              <span class="sub">{{ $t('orgasset.asset.sub') }}</span>
            </div>
            <div class="cb" style="overflow-x: auto; padding-bottom: 6px">
              <table style="min-width: 760px">
                <thead>
                  <tr>
                    <th>{{ $t('orgasset.asset.th.id') }}</th>
                    <th>{{ $t('orgasset.asset.th.name') }}</th>
                    <th>{{ $t('orgasset.asset.th.type') }}</th>
                    <th>{{ $t('orgasset.asset.th.dataClass') }}</th>
                    <th>{{ $t('orgasset.asset.th.pi') }}</th>
                    <th>{{ $t('orgasset.asset.th.crossBorder') }}</th>
                    <th>{{ $t('orgasset.asset.th.mlps') }}</th>
                    <th>{{ $t('orgasset.asset.th.chd') }}</th>
                    <th>{{ $t('orgasset.asset.th.criticality') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in assetRows" :key="r.id">
                    <td class="code">{{ r.id }}</td>
                    <td>{{ $t(r.name) }}</td>
                    <td><span class="pill" :class="r.typePill">{{ $t(r.type) }}</span></td>
                    <td><span class="tag" :class="r.dataClass.cls">{{ $t(r.dataClass.label) }}</span></td>
                    <td>{{ $t(r.pi) }}</td>
                    <td>
                      <span v-if="r.crossBorderTag" class="tag m">{{ $t(r.crossBorder) }}</span>
                      <template v-else>{{ $t(r.crossBorder) }}</template>
                    </td>
                    <td>
                      <span v-if="r.mlps" class="pill blue">{{ $t(r.mlps) }}</span>
                      <template v-else>—</template>
                    </td>
                    <td>{{ $t(r.chd) }}</td>
                    <td><span class="tag" :class="r.crit.cls">{{ $t(r.crit.label) }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- 右：资产类型分布 bars -->
          <div class="card">
            <div class="ch"><h3>{{ $t('orgasset.dist.title') }}</h3></div>
            <div class="cb">
              <div class="bars">
                <div v-for="b in distBars" :key="b.label" class="bar-row">
                  <div class="hd"><span class="nm">{{ $t(b.label) }}</span><b>{{ b.v }}</b></div>
                  <div class="track">
                    <div class="seg2 a" :style="{ width: b.w }"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab3 · 个人信息处理活动(ROPA) ========== -->
      <div v-show="activeTab === 'ropa'" class="tabpane">
        <div class="card">
          <div class="ch">
            <h3>{{ $t('orgasset.ropa.title') }}</h3>
            <span class="sub">{{ $t('orgasset.ropa.sub') }}</span>
            <span class="more">{{ $t('orgasset.ropa.add') }}</span>
          </div>
          <div class="cb" style="overflow-x: auto; padding-bottom: 6px">
            <table style="min-width: 860px">
              <thead>
                <tr>
                  <th>{{ $t('orgasset.ropa.th.activity') }}</th>
                  <th>{{ $t('orgasset.ropa.th.purpose') }}</th>
                  <th>{{ $t('orgasset.ropa.th.piType') }}</th>
                  <th>{{ $t('orgasset.ropa.th.volume') }}</th>
                  <th>{{ $t('orgasset.ropa.th.sensitive') }}</th>
                  <th>{{ $t('orgasset.ropa.th.export') }}</th>
                  <th>{{ $t('orgasset.ropa.th.retention') }}</th>
                  <th>{{ $t('orgasset.ropa.th.owner') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in ropaRows" :key="r.owner">
                  <td>{{ $t(r.activity) }}</td>
                  <td>{{ $t(r.purpose) }}</td>
                  <td>{{ $t(r.piType) }}</td>
                  <td>{{ $t(r.volume) }}</td>
                  <td><span class="tag" :class="r.sensitive.cls">{{ $t(r.sensitive.label) }}</span></td>
                  <td>
                    <span v-if="r.exportTag" class="tag m">{{ $t(r.export) }}</span>
                    <template v-else>{{ $t(r.export) }}</template>
                  </td>
                  <td>{{ $t(r.retention) }}</td>
                  <td>{{ $t(r.owner) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref } from 'vue'
import AppShell from '@/components/AppShell.vue'

// ---- Tab 切换（顺序照搬原型 tabbar：组织架构 / 资产台账 / ROPA）----
const tabs = ['org', 'asset', 'ropa']
const activeTab = ref('org')

// ---- Tab1：组织树节点（lv2/lv3 为原型缩进层级类）----
const treeNodes = [
  { label: 'orgasset.tree.hq', cnt: 'orgasset.tree.people128', lv: '' },
  { label: 'orgasset.tree.payCo', cnt: 'orgasset.tree.people312', lv: 'lv2' },
  { label: 'orgasset.tree.payDepts', cnt: '', lv: 'lv3' },
  { label: 'orgasset.tree.merchantCo', cnt: 'orgasset.tree.people256', lv: 'lv2' },
  { label: 'orgasset.tree.dataCo', cnt: 'orgasset.tree.people189', lv: 'lv2' },
  { label: 'orgasset.tree.overseasCo', cnt: 'orgasset.tree.people98', lv: 'lv2' }
]

// ---- Tab2：资产台账（含数据/合规属性，静态示例值取自原型）----
// dataClass：敏感=tag.h / 内部=tag.m；crit：高=tag.h / 中=tag.m
// crossBorderTag=true 时渲染 tag.m「是」，否则纯文本「否」
// mlps 为空（云擎科技供应商）时渲染 —
const assetRows = [
  { id: 'AST-001', name: 'orgasset.asset.coreGw', type: 'orgasset.asset.typeSystem', typePill: '', dataClass: { cls: 'h', label: 'orgasset.asset.sensitive' }, pi: 'orgasset.asset.yes', crossBorder: 'orgasset.asset.no', crossBorderTag: false, mlps: 'orgasset.asset.l3', chd: 'orgasset.asset.yes', crit: { cls: 'h', label: 'orgasset.asset.high' } },
  { id: 'AST-014', name: 'orgasset.asset.merchantSettle', type: 'orgasset.asset.typeSystem', typePill: '', dataClass: { cls: 'h', label: 'orgasset.asset.sensitive' }, pi: 'orgasset.asset.yes', crossBorder: 'orgasset.asset.no', crossBorderTag: false, mlps: 'orgasset.asset.l3', chd: 'orgasset.asset.yes', crit: { cls: 'h', label: 'orgasset.asset.high' } },
  { id: 'AST-022', name: 'orgasset.asset.crossClearing', type: 'orgasset.asset.typeSystem', typePill: '', dataClass: { cls: 'h', label: 'orgasset.asset.sensitive' }, pi: 'orgasset.asset.yes', crossBorder: 'orgasset.asset.yes', crossBorderTag: true, mlps: 'orgasset.asset.l3', chd: 'orgasset.asset.no', crit: { cls: 'h', label: 'orgasset.asset.high' } },
  { id: 'AST-031', name: 'orgasset.asset.dataWarehouse', type: 'orgasset.asset.typeSystem', typePill: '', dataClass: { cls: 'm', label: 'orgasset.asset.internal' }, pi: 'orgasset.asset.yes', crossBorder: 'orgasset.asset.no', crossBorderTag: false, mlps: 'orgasset.asset.l2', chd: 'orgasset.asset.no', crit: { cls: 'm', label: 'orgasset.asset.mid' } },
  { id: 'AST-039', name: 'orgasset.asset.yunqing', type: 'orgasset.asset.typeVendor', typePill: 'violet', dataClass: { cls: 'h', label: 'orgasset.asset.sensitive' }, pi: 'orgasset.asset.yes', crossBorder: 'orgasset.asset.yes', crossBorderTag: true, mlps: '', chd: 'orgasset.asset.yes', crit: { cls: 'h', label: 'orgasset.asset.high' } }
]

// ---- Tab2 右栏：资产类型分布 bars（seg2.a 强调色）----
const distBars = [
  { label: 'orgasset.dist.system', v: '94', w: '100%' },
  { label: 'orgasset.dist.process', v: '52', w: '55%' },
  { label: 'orgasset.dist.data', v: '26', w: '28%' },
  { label: 'orgasset.dist.vendor', v: '14', w: '15%' }
]

// ---- Tab3：个人信息处理活动记录（ROPA）----
// sensitive：是=tag.h / 否=tag.l；exportTag=true 渲染 tag.m「是」，否则纯文本「否」
const ropaRows = [
  { activity: 'orgasset.ropa.merchantSettle', purpose: 'orgasset.ropa.clearing', piType: 'orgasset.ropa.idCard', volume: 'orgasset.ropa.vMillion', sensitive: { cls: 'h', label: 'orgasset.ropa.yes' }, export: 'orgasset.ropa.no', exportTag: false, retention: 'orgasset.ropa.y10', owner: 'orgasset.ropa.ownerLi' },
  { activity: 'orgasset.ropa.crossClearing', purpose: 'orgasset.ropa.crossPay', piType: 'orgasset.ropa.idTxn', volume: 'orgasset.ropa.vHundredK', sensitive: { cls: 'h', label: 'orgasset.ropa.yes' }, export: 'orgasset.ropa.yes', exportTag: true, retention: 'orgasset.ropa.y10', owner: 'orgasset.ropa.ownerLiu' },
  { activity: 'orgasset.ropa.riskModel', purpose: 'orgasset.ropa.antiFraud', piType: 'orgasset.ropa.deviceBehavior', volume: 'orgasset.ropa.vTenMillion', sensitive: { cls: 'l', label: 'orgasset.ropa.no' }, export: 'orgasset.ropa.no', exportTag: false, retention: 'orgasset.ropa.y5', owner: 'orgasset.ropa.ownerWang' },
  { activity: 'orgasset.ropa.service', purpose: 'orgasset.ropa.quality', piType: 'orgasset.ropa.phoneCall', volume: 'orgasset.ropa.vHundredK', sensitive: { cls: 'l', label: 'orgasset.ropa.no' }, export: 'orgasset.ropa.no', exportTag: false, retention: 'orgasset.ropa.y2', owner: 'orgasset.ropa.ownerChen' }
]
</script>

<style scoped>
/* ========================================================
   样式严格对齐原型 #view-org 区块及其依赖的全局 CSS。
   颜色一律走 tokens.css 语义令牌。
   主题表头底色规则用更具体的类名 .view-orgasset，避免污染其它页。
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

/* ---- 按钮 btn / ghost ---- */
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

/* ---- 布局栅格 g / g-main-side / g-16-1 ---- */
.g {
  display: grid;
  gap: 14px;
  margin-bottom: 14px;
}
.g-main-side {
  grid-template-columns: 1fr 300px;
  align-items: start;
}
.g-16-1 {
  grid-template-columns: 1.6fr 1fr;
}
@media (max-width: 980px) {
  .g-main-side,
  .g-16-1 {
    grid-template-columns: 1fr;
  }
}

/* ---- KPI 卡片 kpibar.k4 ---- */
.kpibar {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 11px;
  margin-bottom: 14px;
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
.kc .v small {
  font-size: 12px;
  color: var(--text-3);
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
/* 当同一卡头同时含 sub 与 more 时（ROPA 卡头），sub 取 margin-left:auto 占位，
   more 紧随其后不再 auto，保持原型「子标题 … 操作」并排在右侧的版式 */
.ch .sub + .more {
  margin-left: 12px;
}
.cb {
  padding: 14px 18px 18px;
}

/* ---- 组织树 tree / tn / dot2 / cnt（含 lv2、lv3 缩进层级）---- */
.tree .tn {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 9px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 12.5px;
}
.tree .tn:hover {
  background: var(--accent-tint);
}
.tree .tn .dot2 {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: var(--accent);
}
.tree .lv2 {
  padding-left: 22px;
}
.tree .lv3 {
  padding-left: 44px;
  color: var(--text-3);
}
.tree .tn .cnt {
  margin-left: auto;
  font-size: 10.5px;
  color: var(--text-3);
}

/* ---- AD 同步态势 sidecard ---- */
.sidecard {
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
  padding: 15px;
  margin-bottom: 14px;
}
.sidecard h4 {
  font-size: 12.5px;
  font-weight: 700;
  margin-bottom: 10px;
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
.seg2.a {
  background: var(--accent);
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

/* ---- 编号 code ---- */
.code {
  font-weight: 700;
  color: var(--accent-strong);
  font-variant-numeric: tabular-nums;
}

/* ---- 类型 / 分级标识 pill ---- */
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 600;
  background: rgba(120, 120, 120, 0.1);
  color: var(--text-2);
}
.pill.blue {
  background: var(--info-tint);
  color: var(--info);
}
.pill.violet {
  background: var(--plum-tint);
  color: var(--plum);
}

/* ---- 数据分级 / 重要性 / 敏感标签 tag ---- */
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
.tag.l {
  background: var(--safe-weak);
  color: var(--safe);
}
.tag.l::before {
  background: var(--safe);
}

/* ---- 状态标签 st（AD 同步态势「正常」）---- */
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
.st.ok {
  color: var(--success);
}
.st.ok .d {
  background: var(--success);
}

/* ---- 朱砂 t-gov 表头底色（用更具体的 .view-orgasset 限定，避免污染其它页）---- */
:global(body.t-gov .view-orgasset thead th) {
  background: var(--accent-tint);
}
</style>
