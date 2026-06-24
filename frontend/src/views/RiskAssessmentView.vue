<!-- =============================================================
     风险评估页（RiskAssessmentView · M2）
     说明：严格按高保真原型「驾驶舱版.html」的 #view-risk 区域 1:1 复原，
     逐区块还原其 DOM 结构与内联 CSS：
       页头 phead（M2 标签 + 标题 + 发起评估按钮）；
       Tab 切换 tabbar（评估任务 / 模板库 / 统一控件库 / KRI 监控）；
       Tab1 评估任务：
         · KPI 五卡 kpibar.k5（进行中/待审批/高风险/逾期/本季完成）
         · 左右栅格 g-16-1：左=评估任务表（编号/对象/模板/进度 prog/风险值 tag/截止/状态）
           右=风险等级分布 bars（五级）+ 评估进度漏斗 funnel（4 段）
       Tab2 模板库：tpls 网格（等保/ISO/PCI/PBOC/27701/供应商/9001 + 新建卡）
       Tab3 统一控件库：左=控件库表（覆盖体系 pill 多标 + 复用数 + 结果）右=复用 Top bars
       Tab4 KRI 监控：KPI 四卡 kpibar.k4 + KRI 指标与阈值表
     下钻（点击「待审批/已生效」任务行）→ 评估报告抽屉视图：
       复原 #view-assess-report 的「风险点清单」「等级分布 donut 下钻」
       与「风险处置与残余风险」表（固有风险→处置决策→残余风险→管理层/责任人接受）。
       该下钻承载了原型中的“固有/残余风险与管理层接受、综合风险指数下钻”要素。
     配色/间距/圆角/字号全部照搬原型；颜色复用 tokens.css 语义令牌，
     极高/极低等级保留原型内联色（#7a1620 / #8aa0b3）。
     文案走 i18n（zh/en 同步），静态示例数值取自原型。
     ============================================================= -->
<template>
  <AppShell>
    <!-- ============ 主视图：评估任务 / 模板库 / 控件库 / KRI ============ -->
    <section v-show="!drill" class="view view-risk">
      <!-- ===== 页头 ===== -->
      <div class="phead">
        <div>
          <div class="kqt">{{ $t('risk.tag') }}</div>
          <h1>{{ $t('risk.title') }}</h1>
        </div>
        <div class="sp"></div>
        <button class="btn" @click="openCreate">{{ $t('risk.newAssess') }}</button>
      </div>

      <!-- 登记弹窗：发起评估 → POST /api/assessments → 刷新列表（端到端写） -->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card">
          <h3>{{ $t('risk.newAssess') }}</h3>
          <label class="fld">{{ $t('risk.create.obj') }}
            <input v-model="form.title" :placeholder="$t('risk.create.objPh')" />
          </label>
          <label class="fld">{{ $t('risk.create.assessor') }}
            <input v-model="form.assessor" />
          </label>
          <label class="fld">{{ $t('risk.create.period') }}
            <input v-model="form.period" placeholder="2026Q2" />
          </label>
          <label class="fld">{{ $t('risk.create.org') }}
            <select v-model="form.orgId">
              <option :value="12">{{ $t('risk.create.orgPay') }}</option>
              <option :value="13">{{ $t('risk.create.orgConsumer') }}</option>
            </select>
          </label>
          <p v-if="createError" class="cerr">{{ createError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCreate = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!form.title || saving" @click="submitCreate">
              {{ saving ? $t('common.submitting') : $t('risk.create.confirm') }}
            </button>
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
          {{ $t('risk.tab.' + t) }}
        </button>
      </div>

      <!-- ========== Tab1 · 评估任务 ========== -->
      <div v-show="activeTab === 'tasks'" class="tabpane">
        <!-- KPI 五卡 -->
        <div class="kpibar k5">
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.active') }}</div>
            <div class="v">14</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.pending') }}</div>
            <div class="v" style="color: var(--warning)">5</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.highRisk') }}</div>
            <div class="v" style="color: var(--danger)">6</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.overdue') }}</div>
            <div class="v" style="color: var(--danger)">3</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.doneQuarter') }}</div>
            <div class="v" style="color: var(--success)">31</div>
          </div>
        </div>

        <div class="g g-16-1">
          <!-- 左：评估任务表（行可点击 → 下钻报告） -->
          <div class="card">
            <div class="ch"><h3>{{ $t('risk.tasks.title') }}</h3></div>
            <table>
              <thead>
                <tr>
                  <th>{{ $t('risk.tasks.th.id') }}</th>
                  <th>{{ $t('risk.tasks.th.obj') }}</th>
                  <th>{{ $t('risk.tasks.th.tpl') }}</th>
                  <th>{{ $t('risk.tasks.th.prog') }}</th>
                  <th>{{ $t('risk.tasks.th.risk') }}</th>
                  <th>{{ $t('risk.tasks.th.due') }}</th>
                  <th>{{ $t('risk.tasks.th.status') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="r in liveTasks"
                  :key="r.id"
                  class="clk"
                  @click="drill = true"
                >
                  <td class="code">{{ r.id }}</td>
                  <td>{{ r.title }}</td>
                  <td>—</td>
                  <td>—</td>
                  <td>
                    <span v-if="r.riskLevel" class="tag" :class="riskCls(r.riskLevel)">{{ $t(riskLabel(r.riskLevel)) }}</span>
                    <span v-else>—</span>
                  </td>
                  <td class="num">—</td>
                  <td>
                    <span class="st" :class="stCls(r.status)"><span class="d"></span>{{ $t(stLabel(r.status)) }}</span>
                  </td>
                </tr>
                <tr v-if="!liveTasks.length">
                  <td colspan="7" style="color: var(--text-3); text-align: center; padding: 18px;">
                    {{ loadError || '暂无评估数据（后端真实数据）' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 右：风险等级分布 + 评估进度漏斗 -->
          <div>
            <!-- 风险等级分布（五级） -->
            <div class="card">
              <div class="ch">
                <h3>{{ $t('risk.levelDist.title') }}</h3>
                <span class="sub">{{ $t('risk.levelDist.sub') }}</span>
              </div>
              <div class="cb">
                <div class="bars">
                  <div v-for="b in levelBars" :key="b.label" class="bar-row">
                    <div class="hd"><span class="nm">{{ $t(b.label) }}</span><b>{{ b.v }}</b></div>
                    <div class="track">
                      <div class="seg2" :class="b.cls" :style="b.style"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 评估进度漏斗（4 段） -->
            <div class="card">
              <div class="ch"><h3>{{ $t('risk.funnel.title') }}</h3></div>
              <div class="cb">
                <div class="funnel">
                  <div
                    v-for="f in funnel"
                    :key="f.key"
                    class="fr"
                    :style="{ width: f.w, background: f.bg }"
                  >
                    {{ $t('risk.funnel.' + f.key) }} {{ f.v }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab2 · 模板库 ========== -->
      <div v-show="activeTab === 'templates'" class="tabpane">
        <div class="tpls">
          <div v-for="c in tplCards" :key="c.key" class="tpl">
            <div class="badge" :style="c.badgeStyle">{{ c.badge }}</div>
            <h4>{{ $t('risk.templates.cards.' + c.key + '.name') }}</h4>
            <div class="desc">{{ $t('risk.templates.cards.' + c.key + '.desc') }}</div>
            <div class="foot">
              <span class="pill">{{ c.ver }}</span>
              <span>{{ $t('risk.templates.cards.' + c.key + '.meta') }}</span>
            </div>
          </div>
          <!-- 新建体系模板（虚线占位卡） -->
          <div
            class="tpl"
            style="
              border-style: dashed;
              display: flex;
              flex-direction: column;
              align-items: center;
              justify-content: center;
              color: var(--accent-strong);
            "
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M12 5v14M5 12h14" />
            </svg>
            <h4 style="margin-top: 6px">{{ $t('risk.templates.newTpl') }}</h4>
          </div>
        </div>
      </div>

      <!-- ========== Tab3 · 统一控件库 ========== -->
      <div v-show="activeTab === 'controls'" class="tabpane">
        <div class="g g-16-1">
          <!-- 左：控件库表 -->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('risk.controls.title') }}</h3>
              <span class="sub">{{ $t('risk.controls.sub') }}</span>
            </div>
            <table>
              <thead>
                <tr>
                  <th>{{ $t('risk.controls.th.id') }}</th>
                  <th>{{ $t('risk.controls.th.ctrl') }}</th>
                  <th>{{ $t('risk.controls.th.systems') }}</th>
                  <th>{{ $t('risk.controls.th.reuse') }}</th>
                  <th>{{ $t('risk.controls.th.result') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in ctrlRows" :key="r.id">
                  <td class="code">{{ r.id }}</td>
                  <td>{{ $t(r.ctrl) }}</td>
                  <td>
                    <span v-for="p in r.systems" :key="p.t" class="pill" :class="p.cls">{{ p.t }}</span>
                  </td>
                  <td class="num">{{ r.reuse }}</td>
                  <td>
                    <span class="st" :class="r.stClass"><span class="d"></span>{{ $t(r.stLabel) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 右：复用 Top bars -->
          <div class="card">
            <div class="ch"><h3>{{ $t('risk.controls.reuseTop.title') }}</h3></div>
            <div class="cb">
              <div class="bars">
                <div v-for="b in reuseTop" :key="b.label" class="bar-row">
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

      <!-- ========== Tab4 · KRI 监控 ========== -->
      <div v-show="activeTab === 'kri'" class="tabpane">
        <!-- KPI 四卡 -->
        <div class="kpibar k4">
          <div class="kc">
            <div class="l">{{ $t('risk.kri.kpi.metrics') }}</div>
            <div class="v">18</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kri.kpi.breach') }}</div>
            <div class="v" style="color: var(--danger)">3</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kri.kpi.sources') }}</div>
            <div class="v">3</div>
            <div class="s">{{ $t('risk.kri.kpi.sourcesSub') }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kri.kpi.collect') }}</div>
            <div class="v" style="color: var(--success)">{{ $t('risk.kri.kpi.collectV') }}</div>
          </div>
        </div>

        <!-- KRI 指标与阈值表 -->
        <div class="card">
          <div class="ch">
            <h3>{{ $t('risk.kri.title') }}</h3>
            <span class="more">{{ $t('risk.kri.config') }}</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>{{ $t('risk.kri.th.metric') }}</th>
                <th>{{ $t('risk.kri.th.source') }}</th>
                <th>{{ $t('risk.kri.th.current') }}</th>
                <th>{{ $t('risk.kri.th.threshold') }}</th>
                <th>{{ $t('risk.kri.th.status') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in kriRows" :key="r.key">
                <td>{{ $t('risk.kri.rows.' + r.key + '.metric') }}</td>
                <td><span class="pill">{{ $t('risk.kri.rows.' + r.key + '.source') }}</span></td>
                <td>{{ $t('risk.kri.rows.' + r.key + '.current') }}</td>
                <td>{{ $t('risk.kri.rows.' + r.key + '.threshold') }}</td>
                <td>
                  <span class="st" :class="r.stClass"><span class="d"></span>{{ $t('risk.kri.st.' + r.st) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- ============ 下钻视图：评估报告（固有/残余风险 + 管理层接受） ============ -->
    <section v-show="drill" class="view view-risk">
      <div class="phead">
        <div>
          <span class="bk" @click="drill = false">{{ $t('risk.report.back') }}</span>
          <div class="kqt">RA-2026-028 · ISO 27001</div>
          <h1>{{ $t('risk.report.title') }}</h1>
        </div>
        <div class="sp"></div>
        <span class="st wait" style="margin-right: 8px"><span class="d"></span>{{ $t('risk.report.pending') }}</span>
        <button class="btn ghost">{{ $t('risk.report.exportPdf') }}</button>
        <button class="btn">{{ $t('risk.report.sign') }}</button>
      </div>

      <!-- 报告 KPI 四卡：综合风险指数下钻 -->
      <div class="kpibar k4">
        <div class="kc">
          <div class="l">{{ $t('risk.report.kpi.riskVal') }}</div>
          <div class="v" style="color: var(--danger)">16.0</div>
          <div class="s">{{ $t('risk.report.kpi.high') }}</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('risk.report.kpi.points') }}</div>
          <div class="v">14</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('risk.report.kpi.highPoints') }}</div>
          <div class="v" style="color: var(--danger)">4</div>
        </div>
        <div class="kc">
          <div class="l">{{ $t('risk.report.kpi.toRemed') }}</div>
          <div class="v">4</div>
        </div>
      </div>

      <div class="g g-16-1">
        <!-- 风险点清单 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('risk.report.list.title') }}</h3></div>
          <table>
            <thead>
              <tr>
                <th>{{ $t('risk.report.list.th.ctrl') }}</th>
                <th>{{ $t('risk.report.list.th.concl') }}</th>
                <th>{{ $t('risk.report.list.th.level') }}</th>
                <th>{{ $t('risk.report.list.th.advice') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in pointRows" :key="i">
                <td>{{ $t('risk.report.list.rows.' + r.key + '.ctrl') }}</td>
                <td>{{ $t('risk.report.list.rows.' + r.key + '.concl') }}</td>
                <td><span class="tag" :class="r.cls">{{ $t('risk.levelDist.' + r.lvl) }}</span></td>
                <td>{{ $t('risk.report.list.rows.' + r.key + '.advice') }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 等级分布 donut 下钻 -->
        <div class="card">
          <div class="ch"><h3>{{ $t('risk.report.donut.title') }}</h3></div>
          <div class="cb">
            <div class="donut-wrap">
              <svg width="90" height="90" viewBox="0 0 42 42">
                <g transform="rotate(-90 21 21)">
                  <circle cx="21" cy="21" r="15.9" fill="none" stroke="rgba(120,120,120,.12)" stroke-width="6" />
                  <circle cx="21" cy="21" r="15.9" pathLength="100" fill="none" stroke="var(--safe)" stroke-width="6" stroke-dasharray="50 50" stroke-dashoffset="-50" />
                  <circle cx="21" cy="21" r="15.9" pathLength="100" fill="none" stroke="var(--warning)" stroke-width="6" stroke-dasharray="21 79" stroke-dashoffset="-29" />
                  <circle cx="21" cy="21" r="15.9" pathLength="100" fill="none" stroke="var(--danger)" stroke-width="6" stroke-dasharray="29 71" />
                </g>
              </svg>
              <div class="dlbl">
                <div class="li">
                  <span class="k"><i style="background: var(--danger)"></i>{{ $t('risk.levelDist.h') }}</span><b>4</b>
                </div>
                <div class="li">
                  <span class="k"><i style="background: var(--warning)"></i>{{ $t('risk.levelDist.m') }}</span><b>3</b>
                </div>
                <div class="li">
                  <span class="k"><i style="background: var(--safe)"></i>{{ $t('risk.levelDist.l') }}</span><b>7</b>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 风险处置与残余风险（固有→处置→残余→管理层/责任人接受） -->
      <div class="card">
        <div class="ch"><h3>{{ $t('risk.report.residual.title') }}</h3></div>
        <div class="cb" style="overflow-x: auto; padding-bottom: 6px">
          <table style="min-width: 740px">
            <thead>
              <tr>
                <th>{{ $t('risk.report.residual.th.point') }}</th>
                <th>{{ $t('risk.report.residual.th.inherent') }}</th>
                <th>{{ $t('risk.report.residual.th.decision') }}</th>
                <th>{{ $t('risk.report.residual.th.measure') }}</th>
                <th>{{ $t('risk.report.residual.th.residual') }}</th>
                <th>{{ $t('risk.report.residual.th.accept') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in residualRows" :key="i">
                <td>{{ $t('risk.report.residual.rows.' + r.key + '.point') }}</td>
                <td><span class="tag" :class="r.inhCls">{{ $t('risk.levelDist.' + r.inhLvl) }}·{{ r.inhVal }}</span></td>
                <td><span class="pill" :class="r.decCls">{{ $t('risk.report.residual.decision.' + r.dec) }}</span></td>
                <td>{{ $t('risk.report.residual.rows.' + r.key + '.measure') }}</td>
                <td><span class="tag" :class="r.resCls">{{ $t('risk.levelDist.' + r.resLvl) }}·{{ r.resVal }}</span></td>
                <td>
                  <span class="st" :class="r.acClass"><span class="d"></span>{{ $t('risk.report.residual.accept.' + r.ac) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'

// ---- Tab 切换 ----
const tabs = ['tasks', 'templates', 'controls', 'kri']
const activeTab = ref('tasks')

// ---- 下钻：评估报告视图开关（点击任务行进入，← 返回）----
const drill = ref(false)

// ---- Tab1：评估任务表（真实后端数据 GET /api/assessments，非静态假数据）----
// 字段以后端为准：后端现有 id/title/riskLevel/status；模板/进度/截止等列后端暂无
// 对应字段（DM-5 C 类缺口），先以「—」占位、不臆造，待后端补字段后再填。
const liveTasks = ref([])
const loadError = ref('')
onMounted(async () => {
  try {
    liveTasks.value = await api.get('/assessments')
  } catch (e) {
    loadError.value = e.message
  }
})

// ---- 发起评估：登记弹窗 → POST /api/assessments → 刷新列表 ----
const showCreate = ref(false)
const saving = ref(false)
const createError = ref('')
const form = reactive({ title: '', assessor: '', period: '', orgId: 12 })
function openCreate() {
  form.title = ''
  form.assessor = ''
  form.period = ''
  form.orgId = 12
  createError.value = ''
  showCreate.value = true
}
async function submitCreate() {
  saving.value = true
  createError.value = ''
  try {
    await api.post('/assessments', {
      orgId: form.orgId,
      title: form.title,
      assessor: form.assessor || null,
      period: form.period || null
    })
    liveTasks.value = await api.get('/assessments') // 创建后刷新列表
    showCreate.value = false
  } catch (e) {
    createError.value = e.message
  } finally {
    saving.value = false
  }
}
// 五级风险等级 → 既有 levelDist 样式键 / i18n 标签键
const LEVEL_KEY = { VERY_HIGH: 'vh', HIGH: 'h', MID: 'm', LOW: 'l', VERY_LOW: 'vl' }
const riskCls = (lv) => LEVEL_KEY[lv] || 'm'
const riskLabel = (lv) => 'risk.levelDist.' + (LEVEL_KEY[lv] || 'm')
// 评估状态 → 样式类 / i18n 标签键（对齐后端 AssessmentStatus）
const STATUS_CLS = { DRAFT: 'wait', IN_PROGRESS: 'doing', PENDING_REVIEW: 'wait', COMPLETED: 'ok' }
const stCls = (s) => STATUS_CLS[s] || 'wait'
const stLabel = (s) => 'risk.assessStatus.' + s

// ---- 风险等级分布（五级）bars ----
// 极高/极低保留原型内联底色；高/中/低复用 seg2.h/m/l 语义色
const levelBars = [
  { label: 'risk.levelDist.vh', v: 4, cls: '', style: { width: '8%', background: '#7a1620' } },
  { label: 'risk.levelDist.h', v: 12, cls: 'h', style: { width: '22%' } },
  { label: 'risk.levelDist.m', v: 64, cls: 'm', style: { width: '58%' } },
  { label: 'risk.levelDist.l', v: 120, cls: 'l', style: { width: '92%' } },
  { label: 'risk.levelDist.vl', v: 48, cls: '', style: { width: '42%', background: '#8aa0b3' } }
]

// ---- 评估进度漏斗（4 段）----
// width / background 完全照搬原型内联值
const funnel = [
  { key: 'started', v: 38, w: '100%', bg: 'var(--accent)' },
  { key: 'filling', v: 31, w: '82%', bg: 'var(--accent-bright)' },
  { key: 'pending', v: 22, w: '58%', bg: 'var(--warning)' },
  { key: 'live', v: 15, w: '40%', bg: 'var(--success)' }
]

// ---- Tab2：模板库卡片（badge 底色照搬原型内联）----
const tplCards = [
  { key: 'mlps', badge: '等保', ver: 'V2.1', badgeStyle: { background: 'var(--info-tint)', color: 'var(--info)' } },
  { key: 'iso', badge: 'ISO', ver: 'V3.0', badgeStyle: { background: 'var(--accent-weak)', color: 'var(--accent-strong)' } },
  { key: 'pci', badge: 'PCI', ver: 'V1.4', badgeStyle: { background: 'var(--plum-tint)', color: 'var(--plum)' } },
  { key: 'pboc', badge: 'PBOC', ver: 'V2.0', badgeStyle: { background: 'var(--warning-tint)', color: '#a87d22' } },
  { key: 'iso27701', badge: '271', ver: 'V1.1', badgeStyle: { background: 'var(--accent-weak)', color: 'var(--accent-strong)' } },
  { key: 'vendor', badge: '供', ver: 'V2.2', badgeStyle: { background: 'var(--info-tint)', color: 'var(--info)' } },
  { key: 'iso9001', badge: '9001', ver: 'V1.0', badgeStyle: { background: 'var(--success-tint)', color: 'var(--success)' } }
]

// ---- Tab3：统一控件库表 ----
const ctrlRows = [
  { id: 'CTL-007', ctrl: 'risk.controls.ctrl.priv', systems: [{ t: 'ISO', cls: 'teal' }, { t: '等保', cls: 'blue' }, { t: 'PBOC', cls: '' }], reuse: 14, stClass: 'ok', stLabel: 'risk.controls.result.ok' },
  { id: 'CTL-019', ctrl: 'risk.controls.ctrl.tls', systems: [{ t: '等保', cls: 'blue' }, { t: 'PCI', cls: 'violet' }], reuse: 9, stClass: 'over', stLabel: 'risk.controls.result.partial' },
  { id: 'CTL-024', ctrl: 'risk.controls.ctrl.acl', systems: [{ t: 'ISO', cls: 'teal' }, { t: '等保', cls: 'blue' }, { t: 'PCI', cls: 'violet' }], reuse: 16, stClass: 'ok', stLabel: 'risk.controls.result.ok' }
]

// ---- 复用 Top bars ----
const reuseTop = [
  { label: 'risk.controls.reuseTop.acl', v: 16, w: '100%' },
  { label: 'risk.controls.reuseTop.priv', v: 14, w: '88%' },
  { label: 'risk.controls.reuseTop.log', v: 11, w: '69%' }
]

// ---- Tab4：KRI 指标与阈值表 ----
const kriRows = [
  { key: 'vuln', st: 'over', stClass: 'over' },
  { key: 'priv', st: 'urgent', stClass: 'over' },
  { key: 'log', st: 'watch', stClass: 'wait' }
]

// ---- 下钻报告：风险点清单 ----
const pointRows = [
  { key: 'acl', lvl: 'h', cls: 'h' },
  { key: 'tls', lvl: 'h', cls: 'h' },
  { key: 'log', lvl: 'l', cls: 'l' }
]

// ---- 下钻报告：风险处置与残余风险（固有→残余→管理层/责任人接受）----
const residualRows = [
  { key: 'shared', inhLvl: 'h', inhCls: 'h', inhVal: 16, dec: 'mitigate', decCls: 'blue', resLvl: 'm', resCls: 'm', resVal: 8, ac: 'pending', acClass: 'wait' },
  { key: 'tls', inhLvl: 'h', inhCls: 'h', inhVal: 15, dec: 'mitigate', decCls: 'blue', resLvl: 'm', resCls: 'm', resVal: 7, ac: 'pending', acClass: 'wait' },
  { key: 'backup', inhLvl: 'm', inhCls: 'm', inhVal: 9, dec: 'accept', decCls: '', resLvl: 'm', resCls: 'm', resVal: 9, ac: 'accepted', acClass: 'ok' }
]
</script>

<style scoped>
/* ========================================================
   样式严格对齐原型 #view-risk 区块及其依赖的全局 CSS。
   颜色一律走 tokens.css 语义令牌（等级极高极低除外，按原型保留内联色）。
   注：表头主题底色规则用更具体的类名 .view-risk，避免污染
   dashboard / external-audit 共用的 .view thead 规则。
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
/* 返回链接 bk（下钻报告页头）*/
.phead .bk {
  font-size: 12px;
  color: var(--accent-strong);
  cursor: pointer;
  font-weight: 600;
  margin-bottom: 5px;
  display: inline-block;
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

/* ---- KPI 卡 kpibar.k5 / k4 ---- */
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
  font-weight: 600;
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

/* ---- 进度条 prog（评估任务表）---- */
.prog {
  height: 6px;
  width: 80px;
  background: rgba(120, 120, 120, 0.13);
  border-radius: 4px;
  overflow: hidden;
  display: inline-block;
  vertical-align: middle;
  margin-right: 6px;
}
.prog i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--accent-bright), var(--accent));
  border-radius: 4px;
}

/* ---- 模板库 tpls / tpl ---- */
.tpls {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 13px;
}
@media (max-width: 980px) {
  .tpls {
    grid-template-columns: repeat(2, 1fr);
  }
}
.tpl {
  background: var(--surface);
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  padding: 15px;
  cursor: pointer;
  box-shadow: var(--shadow-1);
}
.tpl:hover {
  box-shadow: var(--shadow-2);
}
.tpl .badge {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 12px;
  margin-bottom: 10px;
}
.tpl h4 {
  font-size: 13px;
  font-weight: 700;
}
.tpl .desc {
  font-size: 10.5px;
  color: var(--text-3);
  margin-top: 4px;
  line-height: 1.5;
  min-height: 30px;
}
.tpl .foot {
  display: flex;
  gap: 7px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid var(--border-subtle);
  font-size: 10.5px;
  color: var(--text-3);
}

/* ---- 圆环图 donut（报告下钻）---- */
.donut-wrap {
  display: flex;
  align-items: center;
  gap: 18px;
}
.dlbl {
  flex: 1;
}
.dlbl .li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-subtle);
  font-size: 12.5px;
}
.dlbl .li:last-child {
  border: 0;
}
.dlbl .li .k {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-2);
}
.dlbl .li .k i {
  width: 9px;
  height: 9px;
  border-radius: 3px;
}
.dlbl .li b {
  font-weight: 790;
}

/* ---- 表格 table ---- */
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
/* 可点击行（任务表 → 下钻报告）*/
tbody tr.clk {
  cursor: pointer;
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

/* ---- 风险等级标签 tag（五级）---- */
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

/* ---- 体系标识 pill ---- */
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 600;
  background: rgba(120, 120, 120, 0.1);
  color: var(--text-2);
  margin-right: 4px;
}
.pill:last-child {
  margin-right: 0;
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

/* ---- 朱砂 t-gov 表头底色（用更具体的 .view-risk 限定，避免污染其它页）---- */
:global(body.t-gov .view-risk thead th) {
  background: var(--accent-tint);
}

/* ---- 登记弹窗（发起评估）---- */
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
