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
        <!-- 八轮 8-11（A21）：主操作随标签切换——登记册页签是「登记风险」而不是「发起评估」 -->
        <button v-if="activeTab === 'register'" class="btn" :disabled="!canWrite('risk')" @click="openDirectRisk">＋ 登记风险</button>
        <button v-else class="btn" :disabled="!canWrite('risk.create')" :title="canWrite('risk.create') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('risk.newAssess') }}</button>
      </div>

      <!-- 发起评估向导（R1）：① 基本信息 → ② 背景建立（ISO 27005/GB/T 20984 第一阶段）-->
      <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
        <div class="modal-card" :class="{ wide: createStep === 2 }">
          <h3>{{ $t('risk.newAssess') }}<span class="stepdot">{{ createStep }}/2 · {{ createStep === 1 ? '基本信息' : '背景建立' }}</span></h3>

          <template v-if="createStep === 1">
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
                <option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option>
              </select>
            </label>
            <!-- 表单引擎 P1：选模板则评估按该模板的 .docx 表单填写规范报告（可不选）-->
            <label class="fld">评估模板（选模板则用其报告表单填写，可不选）
              <select v-model="form.templateId">
                <option :value="null">— 不使用模板表单 —</option>
                <!-- A12：仅已发布模板可实例化（M2-9/15），并滤掉测试残渣 -->
                <option v-for="t in templates.filter(x => x.status === 'PUBLISHED')" :key="t.id" :value="t.id">{{ t.name }}（{{ t.code }}）</option>
              </select>
            </label>
          </template>

          <template v-else>
            <label class="fld">评估范围与边界（系统 / 业务 / 部门 / 场所）
              <textarea v-model="ctx.scope" rows="2" placeholder="如 支付核心网关及其数据库、机房 A 区；不含灾备中心"></textarea>
            </label>
            <label class="fld">评估目的与背景
              <textarea v-model="ctx.objective" rows="2" placeholder="如 满足等保三级年度自评与内部风控要求"></textarea>
            </label>
            <div class="fld">依据标准（多选）
              <div class="opt-grid">
                <label v-for="b in BASIS_OPTS" :key="b.v" class="opt" :class="{ on: ctx.basis.includes(b.v) }"><input type="checkbox" :value="b.v" v-model="ctx.basis" /><span>{{ b.l }}</span></label>
                </div>
            </div>
            <div class="fld">方式方法（多选）
              <div class="opt-grid">
                <label v-for="m in METHOD_OPTS" :key="m.v" class="opt" :class="{ on: ctx.methods.includes(m.v) }"><input type="checkbox" :value="m.v" v-model="ctx.methods" /><span>{{ m.l }}</span></label>
                </div>
            </div>
            <label class="fld">评估准则
              <textarea v-model="ctx.criteria" rows="2"></textarea>
            </label>
            <label class="fld">评估组成员
              <input v-model="ctx.team" placeholder="如 张三（组长）、李四、外部顾问王五" />
            </label>
            <div class="fld-2col">
              <label class="fld">开始日<input type="date" v-model="ctx.startDate" /></label>
              <label class="fld">结束日<input type="date" v-model="ctx.endDate" /></label>
            </div>
          </template>

          <p v-if="createError" class="cerr">{{ createError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="createStep === 1 ? (showCreate = false) : (createStep = 1)">{{ createStep === 1 ? $t('common.cancel') : '上一步' }}</button>
            <button v-if="createStep === 1" class="btn" :disabled="!form.title" @click="createStep = 2">下一步 · 背景建立</button>
            <button v-else class="btn" :disabled="saving" @click="submitCreate">{{ saving ? $t('common.submitting') : $t('risk.create.confirm') }}</button>
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
        <!-- KPI 五卡（七轮 7-5：接真值——由评估任务实时聚合，不再写死示意数）-->
        <div class="kpibar k5">
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.active') }}</div>
            <div class="v">{{ kpiCount('IN_PROGRESS') }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.pending') }}</div>
            <div class="v" style="color: var(--warning)">{{ kpiCount('PENDING_REVIEW') }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.highRisk') }}</div>
            <div class="v" style="color: var(--danger)">{{ kpiHighRisk }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.overdue') }}</div>
            <div class="v" style="color: var(--danger)">{{ kpiOverdue }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kpi.doneQuarter') }}</div>
            <div class="v" style="color: var(--success)">{{ kpiCount('COMPLETED') }}</div>
          </div>
        </div>

        <div class="g g-16-1">
          <!-- 左：评估任务表（行可点击 → 下钻报告） -->
          <div class="card">
            <!-- 评估计划管理（需求 4.2.1：年度/季度/临时专项，排期→启动生成评估→完成）-->
            <div class="ch"><h3>评估计划</h3><span class="cnt">{{ plans.length }}</span>
              <button class="btn ghost sm" style="margin-left:auto" :disabled="!canWrite('risk')" @click="openPlan">＋ 排期计划</button>
            </div>
            <div class="cb" style="padding-top:0;padding-bottom:6px">
              <table>
                <thead><tr><th>计划</th><th>周期</th><th>计划日期</th><th>状态</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="pl in plans" :key="pl.id">
                    <td><b>{{ pl.title }}</b></td>
                    <td><span class="pill">{{ PT_LABEL[pl.periodType] || pl.periodType }}</span></td>
                    <td class="num">{{ pl.plannedDate || '—' }}</td>
                    <td><span class="st" :class="PLST_CLS[pl.status]"><span class="d"></span>{{ PLST_LABEL[pl.status] }}</span></td>
                    <td class="ops">
                      <template v-if="canWrite('risk')">
                        <button v-if="pl.status === 'PLANNED'" class="btn sm" @click="startPlan(pl)">启动</button>
                        <button v-if="pl.status === 'STARTED'" class="btn ghost sm" @click="donePlan(pl)">完成</button>
                      </template>
                      <span v-if="pl.assessmentId" class="muted">→ 评估 {{ pl.assessmentId }}</span>
                    </td>
                  </tr>
                  <tr v-if="!plans.length"><td colspan="5" class="emptyrow">暂无评估计划，点「＋ 排期计划」。</td></tr>
                </tbody>
              </table>
            </div>
            <div class="ch"><h3>{{ $t('risk.tasks.title') }}</h3>
              <label class="opt" style="margin-left:auto;height:26px;font-size:11.5px">
                <input type="checkbox" v-model="showCancelled" /><span>显示已作废</span>
              </label>
            </div>
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
                  <th>管理</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="r in visibleTasks"
                  :key="r.id"
                  class="clk"
                  @click="drillInto(r)"
                >
                  <td class="code">{{ r.id }}</td>
                  <td>{{ r.title }}</td>
                  <!-- A13：体系模板/进度/截止三列接真值（原为硬编码占位 —） -->
                  <td>{{ tplName(r.templateId) }}</td>
                  <td class="num">{{ taskProgress(r.status) }}</td>
                  <td>
                    <span v-if="r.riskLevel && r.status !== 'DRAFT'" class="tag" :class="riskCls(r.riskLevel)">{{ $t(riskLabel(r.riskLevel)) }}</span>
                    <span v-else>—</span>
                  </td>
                  <td class="num">{{ r.endDate || '—' }}</td>
                  <td>
                    <span class="st" :class="stCls(r.status)"><span class="d"></span>{{ $t(stLabel(r.status)) }}</span>
                  </td>
                  <td class="ops" @click.stop>
                    <template v-if="canWrite('risk')">
                      <!-- 七轮 7-11（B40 P0）：生命周期流转按钮——后端状态机早已齐备，此前 UI 无任何入口 -->
                      <button v-if="r.status === 'DRAFT'" class="mini" @click="flowAction(r, 'start', '启动评估')">启动</button>
                      <button v-if="r.status === 'IN_PROGRESS'" class="mini" @click="flowAction(r, 'submit', '提交复核')">提交复核</button>
                      <button v-if="r.status === 'PENDING_REVIEW'" class="mini" style="color:var(--success);border-color:var(--success)" @click="flowAction(r, 'complete', '复核通过并完成')">通过</button>
                      <button v-if="r.status === 'PENDING_REVIEW'" class="mini" @click="rejectTask(r)">驳回</button>
                      <button v-if="r.status === 'DRAFT'" class="mini danger" @click="deleteTask(r)">删除</button>
                      <button v-else-if="r.status === 'IN_PROGRESS' || r.status === 'PENDING_REVIEW'" class="mini danger" @click="cancelTask(r)">作废</button>
                      <span v-else-if="r.status === 'CANCELLED' || r.status === 'COMPLETED'" class="muted" style="font-size:11px">{{ r.status === 'CANCELLED' ? '已作废' : '定稿' }}</span>
                    </template>
                  </td>
                </tr>
                <tr v-if="!visibleTasks.length">
                  <td colspan="8" style="color: var(--text-3); text-align: center; padding: 18px;">
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

      <!-- ========== Tab1.5 · 风险登记册（六轮 #2/#5：ISO 31000 组织级风险台账，跨评估聚合 + 处置追踪）========== -->
      <div v-show="activeTab === 'register'" class="tabpane">
        <!-- 登记册 KPI -->
        <div class="kpibar k5">
          <div class="kc"><div class="l">风险总数</div><div class="v">{{ regRows.length }}</div></div>
          <div class="kc"><div class="l">待处置</div><div class="v" style="color: var(--warning)">{{ regCount('OPEN') }}</div></div>
          <div class="kc"><div class="l">处置中</div><div class="v">{{ regCount('IN_TREATMENT') }}</div></div>
          <div class="kc"><div class="l">已关闭</div><div class="v" style="color: var(--success)">{{ regClosed }}</div></div>
          <div class="kc"><div class="l">高/极高残余</div><div class="v" style="color: var(--danger)">{{ regHigh }}</div></div>
        </div>

        <!-- 风险准则卡（组织级评估准则，ISO 27005 风险准则要求）-->
        <div class="card" style="margin-bottom: 14px">
          <div class="ch"><h3>风险准则（组织级）</h3></div>
          <div class="cb" style="padding-top: 4px">
            <p style="margin: 0; font-size: 12.5px; line-height: 1.8; color: var(--text-2)">
              可能性五级 × 影响五级 → 风险矩阵定级（极低/低/中/高/极高）；残余风险为高/极高的发现，
              须经管理层「风险接受」批准方可关闭；各评估任务的具体准则在其「评估背景」中登记。
            </p>
          </div>
        </div>

        <!-- B22 加权风险指数条（未闭环风险按有效等级加权；点等级芯片下钻） -->
        <div v-if="riskIdx" class="card" style="margin-bottom:14px">
          <div class="cb" style="display:flex;align-items:center;gap:20px;flex-wrap:wrap">
            <div>
              <div style="font-size:24px;font-weight:800;font-family:var(--font-display, inherit)">{{ riskIdx.weightedScore }}</div>
              <div style="font-size:11px;color:var(--text-3)">加权风险指数 · {{ riskIdx.openCount }} 项未闭环 · 均权 {{ riskIdx.avgWeight }}</div>
            </div>
            <div style="display:flex;gap:8px;flex-wrap:wrap">
              <button v-for="l in DRILL_LV" :key="l[0]" class="pill" :style="{ cursor:'pointer', background: regFltLevel===l[0] ? l[2] : 'var(--surface-2, rgba(0,0,0,.03))', color: regFltLevel===l[0] ? '#fff' : 'var(--text-2)', border:'1px solid ' + l[2] }" @click="drillLevel(l[0])">{{ l[1] }} {{ riskIdx.byLevel[l[0]] || 0 }}</button>
            </div>
          </div>
        </div>

        <!-- 台账（筛选 + 表格；行点击进入来源评估下钻）-->
        <div class="card">
          <div class="ch"><h3>风险登记册</h3><span class="cnt">{{ regFiltered.length }}</span>
            <div style="margin-left: auto; display: flex; gap: 8px">
              <select v-model="regFltLevel" class="selmini"><option value="">全部等级</option><option v-for="l in DRILL_LV" :key="l[0]" :value="l[0]">{{ l[1] }}</option></select>
              <select v-model="regFltStatus" class="selmini"><option value="">全部状态</option><option value="OPEN">待处置</option><option value="IN_TREATMENT">处置中</option><option value="DONE">已处置</option><option value="VERIFIED">已验证</option></select>
              <!-- B46：风险登记册导出（当前筛选态） -->
              <button class="mini" :disabled="!regFiltered.length" @click="exportRegister">导出 CSV</button>
            </div>
          </div>
          <div class="cb" style="padding-top: 0">
            <table>
              <thead><tr><th>#</th><th>风险发现</th><th>来源评估</th><th>固有</th><th>残余</th><th>处置方式</th><th>状态</th><th>更新时间</th></tr></thead>
              <tbody>
                <tr v-for="r in regFiltered" :key="r.id" style="cursor: pointer" @click="regGoAssessment(r)">
                  <td class="num">{{ r.id }}</td>
                  <td><b>{{ r.title }}</b></td>
                  <td style="color: var(--text-2)">{{ r.assessmentTitle }}<span v-if="r.source" class="pill" style="margin-left:6px">{{ SRC_TXT[r.source] || r.source }}</span></td>
                  <td><span v-if="r.inherentLevel" class="tag" :class="riskCls(r.inherentLevel)">{{ lvText(r.inherentLevel) }}</span><span v-else>—</span></td>
                  <td><span v-if="r.residualLevel" class="tag" :class="riskCls(r.residualLevel)">{{ lvText(r.residualLevel) }}</span><span v-else>—</span></td>
                  <td>{{ TD_LABEL[r.treatmentDecision] || '—' }}</td>
                  <td><span class="st" :class="REG_ST_CLS[r.status]"><span class="d"></span>{{ REG_ST_TXT[r.status] || r.status }}</span></td>
                  <td class="num">{{ (r.updatedAt || '').slice(0, 10) }}</td>
                </tr>
                <tr v-if="!regFiltered.length"><td colspan="8" style="text-align: center; padding: 18px; color: var(--text-2)">暂无符合条件的风险发现——发起评估并登记风险后，将自动汇入登记册。</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 登记风险弹窗（八轮 8-11/C11：事件/漏洞驱动的日常风险直登，不必先造评估）-->
        <div v-if="showDirect" class="modal-mask" @click.self="showDirect = false">
          <div class="modal-card">
            <h3>登记风险（日常直登）</h3>
            <label class="fld">风险描述<input v-model="df2.title" placeholder="如 生产库高危漏洞 CVE-2026-xxxx 未修复" /></label>
            <label class="fld">固有等级
              <select v-model="df2.inherentLevel">
                <option value="VERY_HIGH">极高</option><option value="HIGH">高</option>
                <option value="MID">中</option><option value="LOW">低</option><option value="VERY_LOW">极低</option>
              </select>
            </label>
            <label class="fld">来源
              <select v-model="df2.source">
                <option value="EVENT">安全事件</option><option value="VULN">漏洞扫描</option>
                <option value="AUDIT">审计发现</option><option value="MANUAL">手工识别</option>
              </select>
            </label>
            <label class="fld">所属组织<select v-model.number="df2.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
            <p v-if="directErr" class="cerr">{{ directErr }}</p>
            <div class="modal-actions">
              <button class="btn ghost" @click="showDirect = false">取消</button>
              <button class="btn" :disabled="!df2.title || directBusy" @click="submitDirect">{{ directBusy ? '提交中…' : '确认登记' }}</button>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab2 · 模板库（真实后端 GET /api/assessment-templates）========== -->
      <div v-show="activeTab === 'templates'" class="tabpane">
        <div class="tpls">
          <div v-for="t in templates" :key="t.id" class="tpl">
            <div class="badge" :style="fwBadge(t.framework)">{{ fwShort(t.framework) }}</div>
            <h4>{{ t.name }}</h4>
            <div class="desc">{{ t.description || '—' }}</div>
            <div class="foot">
              <span class="pill">{{ t.code }}</span>
              <span class="st" :class="TPL_STATUS_CLS[t.status]"><span class="d"></span>{{ $t('risk.templates.tstatus.' + t.status) }}</span>
            </div>

            <!-- 报告表单状态（只读；内置模板由系统预装标准表单，开箱即用；换表单到「预览详情」里管理）-->
            <div class="formbox">
              <div class="fb-row">
                <span class="fb-l">报告表单：</span>
                <span v-if="tplFormState(t.id).active" class="fb-active">
                  ✓ 可用 · {{ tplFormState(t.id).active.name }}（v{{ tplFormState(t.id).active.versionNo }}）
                  <!-- 八轮 8-8（C5 止血）：v1 通用骨架如实标注；等保/PBOC 已差异化（名称带「·」标识） -->
                  <span v-if="t.owner === 'platform' && tplFormState(t.id).active.name === '内置标准表单'"
                        class="pill" style="margin-left:6px; color:#a87d22" title="该体系的差异化表单分批推进中；正式使用可在预览详情上传行业表单">通用骨架</span>
                </span>
                <span v-else class="fb-none">未配置（到「预览详情」上传）</span>
              </div>
              <div v-if="tplFormState(t.id).error" class="fb-err">{{ tplFormState(t.id).error }}</div>
            </div>
            <div class="fb-actions" style="margin-top:8px">
              <button class="btn ghost sm" @click="openTplDetail(t)">预览详情</button>
              <button class="btn ghost sm" :disabled="!canWriteRisk" @click="openTplClone(t)">克隆</button>
              <!-- 六轮 #1：内置模板（owner=platform）是集团基线不可删；自建/克隆可删（被评估引用时后端拒绝并提示） -->
              <button v-if="t.owner !== 'platform'" class="mini danger" :disabled="!canWriteRisk" @click="deleteTpl(t)">删除</button>
            </div>
          </div>
          <!-- 新建体系模板（虚线卡 → 真实创建弹窗） -->
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
            @click="openRefModal('template')"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M12 5v14M5 12h14" />
            </svg>
            <h4 style="margin-top: 6px">{{ $t('risk.templates.newTpl') }}</h4>
          </div>
        </div>
      </div>

      <!-- ========== Tab3 · 统一控件库（真实后端 GET /api/controls + /{id}/mappings）========== -->
      <div v-show="activeTab === 'controls'" class="tabpane">
        <div class="g g-16-1">
          <!-- 左：控件库表 -->
          <div class="card">
            <div class="ch">
              <h3>{{ $t('risk.controls.title') }}</h3>
              <span class="sub">{{ $t('risk.controls.sub') }}</span>
              <button class="btn ghost sm" style="margin-left: 10px" @click="openRefModal('control')">{{ $t('risk.controls.newCtrl') }}</button>
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
                <tr v-for="c in controls" :key="c.id">
                  <td class="code">{{ c.code }}</td>
                  <td>{{ c.name }}</td>
                  <td>
                    <span v-for="m in (ctrlMappings[c.id] || [])" :key="m.id" class="pill" :class="fwPill(m.framework)">{{ fwShort(m.framework) }}</span>
                    <span v-if="!(ctrlMappings[c.id] || []).length" class="muted">{{ $t('risk.controls.noMap') }}</span>
                  </td>
                  <td class="num">{{ ctrlReuse(c.id) }}</td>
                  <td>
                    <span class="st" :class="CTRL_STATUS_CLS[c.status]"><span class="d"></span>{{ $t('risk.controls.cstatus.' + c.status) }}</span>
                    <div style="margin-top:5px;display:flex;gap:6px;align-items:center;justify-content:center">
                      <span v-if="ctrlReusable[c.id]" class="pill" style="background:var(--safe-weak);color:var(--safe)" :title="'测试可复用 · 有效至 ' + ctrlReusable[c.id].validUntil">测试可复用</span>
                      <span v-else class="muted" style="font-size:11px">需测试</span>
                      <button v-if="canWrite('risk') && c.status === 'ACTIVE'" class="mini-x" title="记录控件有效性测试" @click="openCtrlTest(c)">✎测</button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!controls.length">
                  <td colspan="5" class="emptyrow">{{ $t('risk.controls.empty') }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 右：复用 Top bars（按真实映射数排序）-->
          <div class="card">
            <div class="ch"><h3>{{ $t('risk.controls.reuseTop.title') }}</h3></div>
            <div class="cb">
              <div class="bars">
                <div v-for="b in reuseTopLive" :key="b.code" class="bar-row">
                  <div class="hd"><span class="nm">{{ b.name }}</span><b>{{ b.v }}</b></div>
                  <div class="track">
                    <div class="seg2 a" :style="{ width: b.w }"></div>
                  </div>
                </div>
                <div v-if="!reuseTopLive.length" class="muted">{{ $t('risk.controls.empty') }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab4 · KRI 监控（真实后端 GET /api/kris）========== -->
      <div v-show="activeTab === 'kri'" class="tabpane">
        <!-- KPI 四卡（真实统计：指标数/严重/预警/正常）-->
        <div class="kpibar k4">
          <div class="kc">
            <div class="l">{{ $t('risk.kri.kpi.metrics') }}</div>
            <div class="v">{{ kriStat.total }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kri.kpi.critical') }}</div>
            <div class="v" style="color: var(--danger)">{{ kriStat.critical }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kri.kpi.warning') }}</div>
            <div class="v" style="color: #a87d22">{{ kriStat.warning }}</div>
          </div>
          <div class="kc">
            <div class="l">{{ $t('risk.kri.kpi.normal') }}</div>
            <div class="v" style="color: var(--success)">{{ kriStat.normal }}</div>
          </div>
        </div>

        <!-- KRI 指标与阈值表 -->
        <div class="card">
          <div class="ch">
            <h3>{{ $t('risk.kri.title') }}</h3>
            <button class="mini" style="margin-left:auto" :disabled="!kris.length" @click="exportKris">导出 CSV</button>
            <span class="more" @click="openRefModal('kri')">{{ $t('risk.kri.newKri') }}</span>
          </div>
          <table>
            <thead>
              <tr>
                <th>{{ $t('risk.kri.th.metric') }}</th>
                <th>{{ $t('risk.kri.th.owner') }}</th>
                <th>{{ $t('risk.kri.th.current') }}</th>
                <th>{{ $t('risk.kri.th.threshold') }}</th>
                <th>{{ $t('risk.kri.th.status') }}</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="k in kris" :key="k.id">
                <tr>
                  <td>{{ k.name }} <span class="code">{{ k.code }}</span></td>
                  <td><span class="pill">{{ k.owner || '—' }}</span></td>
                  <td class="num">{{ k.currentValue == null ? '—' : k.currentValue }}<span v-if="k.unit" class="muted"> {{ k.unit }}</span></td>
                  <td class="num">{{ k.thresholdWarning }} / {{ k.thresholdCritical }}</td>
                  <td>
                    <span class="st" :class="kriStCls(k.currentStatus)"><span class="d"></span>{{ $t('risk.kri.cstatus.' + k.currentStatus) }}</span>
                  </td>
                  <!-- 八轮 8-10（B41）：测量值终于有了录入通道；趋势=近 N 次测量展开 -->
                  <td style="white-space:nowrap">
                    <template v-if="canWrite('risk')">
                      <button class="mini" @click="recordKri(k)">录入测量值</button>
                      <button class="mini" @click="toggleKriHistory(k)">{{ kriHistoryId === k.id ? '收起' : '趋势' }}</button>
                    </template>
                  </td>
                </tr>
                <tr v-if="kriHistoryId === k.id">
                  <td colspan="6" style="background: var(--bg); padding: 8px 14px">
                    <span v-if="!kriHistory.length" class="muted">暂无历史测量</span>
                    <span v-for="m in kriHistory" :key="m.id" class="pill" style="margin-right: 8px"
                          :style="{ color: m.status === 'CRITICAL' ? 'var(--danger)' : (m.status === 'WARNING' ? '#a87d22' : 'var(--success)') }">
                      {{ (m.measuredAt || '').slice(5, 10) }}：{{ m.value }}{{ k.unit || '' }}
                    </span>
                  </td>
                </tr>
              </template>
              <tr v-if="!kris.length">
                <td colspan="6" class="emptyrow">{{ $t('risk.kri.empty') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ========== Tab5 · A-T-V 建模（资产-威胁-脆弱性 → 风险场景，需求 4.5.2）========== -->
      <div v-show="activeTab === 'atv'" class="tabpane">
        <div class="g g-16-1">
          <!-- 左：风险场景表 -->
          <div class="card">
            <div class="ch"><h3>风险场景（资产 × 威胁 × 脆弱性）</h3><span class="cnt">{{ scenarios.length }}</span>
              <button class="mini" style="margin-left:auto" :disabled="!scenarios.length" @click="exportScenarios">导出 CSV</button>
              <button class="btn sm" :disabled="!canWrite('risk')" @click="openScenario">＋ 建模场景</button>
            </div>
            <div class="cb" style="overflow-x:auto;padding-top:0">
              <table style="min-width:720px">
                <thead><tr><th>资产</th><th>威胁</th><th>脆弱性</th><th>可能性×影响</th><th>固有等级</th><th>说明</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="s in scenarios" :key="s.id">
                    <td>{{ assetNameOf(s.assetId) }}</td>
                    <td>{{ threatNameOf(s.threatId) }}</td>
                    <td>{{ vulnNameOf(s.vulnerabilityId) }}</td>
                    <td class="num">{{ s.likelihood }} × {{ s.impact }} = {{ s.likelihood * s.impact }}</td>
                    <td><span class="tag" :class="riskCls(s.inherentLevel)">{{ $t(riskLabel(s.inherentLevel)) }}</span></td>
                    <td class="muted">{{ s.description || '—' }}</td>
                    <td style="white-space:nowrap">
                      <button v-if="canWrite('risk')" class="btn ghost sm" @click="openToFinding(s)">生成发现</button>
                      <!-- 八轮 8-10（B44）：场景可删（已派生发现的后端拒绝，保溯源） -->
                      <button v-if="canWrite('risk')" class="mini danger" @click="deleteScenario(s)">删</button>
                    </td>
                  </tr>
                  <tr v-if="!scenarios.length"><td colspan="7" class="emptyrow">暂无风险场景，点「＋ 建模场景」。</td></tr>
                </tbody>
              </table>
              <p v-if="atvErr" class="cerr">{{ atvErr }}</p>
            </div>
          </div>
          <!-- 右：威胁库 / 脆弱性库 -->
          <div>
            <div class="card">
              <div class="ch"><h3>威胁库</h3><span class="cnt">{{ threats.length }}</span>
                <button class="btn ghost sm" style="margin-left:auto" :disabled="!canWrite('risk')" @click="openAtvRef('threat')">＋</button>
              </div>
              <div class="cb" style="padding-top:0">
                <!-- 八轮 8-10（B44）：三库可编辑/删除（被场景引用的删除会被后端 409 拦截） -->
                <div v-for="t2 in threats" :key="t2.id" class="atv-row"><span class="code">{{ t2.code }}</span>{{ t2.name }}<span class="pill" style="margin-left:auto">{{ t2.category || '—' }}</span>
                  <template v-if="canWrite('risk')">
                    <button class="mini-x" title="编辑" @click="editAtvRef('threats', t2)">✎</button>
                    <button class="mini-x" title="删除" @click="deleteAtvRef('threats', t2)">✕</button>
                  </template>
                </div>
                <div v-if="!threats.length" class="hint">暂无威胁条目</div>
              </div>
            </div>
            <div class="card" style="margin-top:14px">
              <div class="ch"><h3>脆弱性库</h3><span class="cnt">{{ vulns.length }}</span>
                <button class="btn ghost sm" style="margin-left:auto" :disabled="!canWrite('risk')" title="从漏扫结果批量导入（按编码去重）" @click="openScanImport">⇪ 漏扫导入</button>
                <button class="btn ghost sm" style="margin-left:8px" :disabled="!canWrite('risk')" @click="openAtvRef('vuln')">＋</button>
              </div>
              <div class="cb" style="padding-top:0">
                <div v-for="v2 in vulns" :key="v2.id" class="atv-row"><span class="code">{{ v2.code }}</span>{{ v2.name }}<span class="pill" style="margin-left:auto">{{ v2.category || '—' }}</span>
                  <template v-if="canWrite('risk')">
                    <button class="mini-x" title="编辑" @click="editAtvRef('vulnerabilities', v2)">✎</button>
                    <button class="mini-x" title="删除" @click="deleteAtvRef('vulnerabilities', v2)">✕</button>
                  </template>
                </div>
                <div v-if="!vulns.length" class="hint">暂无脆弱性条目</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 排期评估计划弹窗 -->
      <div v-if="showPlan" class="modal-mask" @click.self="showPlan = false">
        <div class="modal-card">
          <h3>排期评估计划</h3>
          <label class="fld">计划主题<input v-model="plf.title" placeholder="如 2026 年度等保自评" /></label>
          <label class="fld">周期类型
            <select v-model="plf.periodType"><option value="ANNUAL">年度</option><option value="QUARTERLY">季度</option><option value="ADHOC">临时专项</option></select>
          </label>
          <label class="fld">计划开始日期<input type="date" v-model="plf.plannedDate" /></label>
          <label class="fld">评估模板（可空；带模板启动即进表单引擎）
            <select v-model.number="plf.templateId"><option :value="null">— 不使用模板 —</option><option v-for="t2 in templates.filter(x => x.status === 'PUBLISHED')" :key="t2.id" :value="t2.id">{{ t2.name }}</option></select>
          </label>
          <label class="fld">所属组织<select v-model.number="plf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showPlan = false">取消</button>
            <button class="btn" :disabled="!plf.title || refSaving" @click="submitPlan">确认排期</button>
          </div>
        </div>
      </div>

      <!-- B20 控件测试记录弹窗 -->
      <div v-if="showCtrlTest" class="modal-mask" @click.self="showCtrlTest = false">
        <div class="modal-card">
          <h3>记录控件测试 · {{ ctlt.controlName }}</h3>
          <p style="font-size:11.5px;color:var(--text-3);margin:-6px 0 10px">测试结论「有效(EFFECTIVE)且在有效期内」时，新的审计/评估可直接复用而不必重测。</p>
          <label class="fld">测试类型
            <select v-model="ctlt.testType"><option value="OPERATING">运行有效性</option><option value="DESIGN">设计有效性</option></select>
          </label>
          <label class="fld">测试结论
            <select v-model="ctlt.result"><option value="EFFECTIVE">有效</option><option value="PARTIAL">部分有效</option><option value="DEFICIENT">缺陷</option></select>
          </label>
          <label class="fld">有效期至（复用窗口，可空则不参与复用）<input type="date" v-model="ctlt.validUntil" /></label>
          <label class="fld">说明<input v-model="ctlt.note" placeholder="测试范围/证据摘要，可空" /></label>
          <p v-if="ctrlTestErr" class="cerr">{{ ctrlTestErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCtrlTest = false">取消</button>
            <button class="btn" :disabled="refSaving" @click="submitCtrlTest">{{ refSaving ? '提交中…' : '确认记录' }}</button>
          </div>
        </div>
      </div>

      <!-- B44 漏扫导入弹窗 -->
      <div v-if="showScanImport" class="modal-mask" @click.self="showScanImport = false">
        <div class="modal-card" style="width:600px">
          <h3>漏扫结果导入 · 脆弱性库</h3>
          <p style="font-size:11.5px;color:var(--text-3);margin:-6px 0 10px">每行一条：<code>编码,名称,分类,描述</code>（分类/描述可空）。按编码去重——库内已有的编码跳过，不覆盖人工维护条目。</p>
          <label class="fld">所属组织<select v-model.number="scanForm.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <label class="fld">粘贴漏扫条目
            <textarea v-model="scanForm.text" rows="8" style="font-family:monospace;font-size:12px" placeholder="CVE-2024-1234,OpenSSL 越界读,加密,TLS 心跳缓冲越界&#10;WEAK-PWD-01,默认口令未修改,访问控制,"></textarea>
          </label>
          <p v-if="scanResult" style="font-size:12px;color:var(--safe)">导入完成：新增 {{ scanResult.imported }} 条，跳过 {{ scanResult.skipped }} 条（已存在或格式不完整）。</p>
          <p v-if="atvErr" class="cerr">{{ atvErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showScanImport = false">关闭</button>
            <button class="btn" :disabled="!scanForm.text.trim() || refSaving" @click="submitScanImport">{{ refSaving ? '导入中…' : '导入' }}</button>
          </div>
        </div>
      </div>

      <!-- 建模风险场景弹窗（A-T-V）-->
      <div v-if="showScenario" class="modal-mask" @click.self="showScenario = false">
        <div class="modal-card">
          <h3>建模风险场景</h3>
          <label class="fld">资产
            <select v-model.number="scf.assetId"><option :value="0" disabled>— 选择资产 —</option><option v-for="a in atvAssets" :key="a.id" :value="a.id">{{ a.name }}</option></select>
          </label>
          <label class="fld">威胁
            <select v-model.number="scf.threatId"><option :value="0" disabled>— 选择威胁 —</option><option v-for="t2 in threats" :key="t2.id" :value="t2.id">{{ t2.code }} · {{ t2.name }}</option></select>
          </label>
          <label class="fld">脆弱性
            <select v-model.number="scf.vulnerabilityId"><option :value="0" disabled>— 选择脆弱性 —</option><option v-for="v2 in vulns" :key="v2.id" :value="v2.id">{{ v2.code }} · {{ v2.name }}</option></select>
          </label>
          <label class="fld">可能性（1~5）<input v-model.number="scf.likelihood" type="number" min="1" max="5" /></label>
          <label class="fld">影响（1~5）<input v-model.number="scf.impact" type="number" min="1" max="5" /></label>
          <label class="fld">说明<input v-model="scf.description" placeholder="可空" /></label>
          <p v-if="atvErr" class="cerr">{{ atvErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showScenario = false">取消</button>
            <button class="btn" :disabled="!scf.assetId || !scf.threatId || !scf.vulnerabilityId || refSaving" @click="submitScenario">确认建模</button>
          </div>
        </div>
      </div>

      <!-- 新建威胁/脆弱性弹窗 -->
      <div v-if="atvRefKind" class="modal-mask" @click.self="atvRefKind = null">
        <div class="modal-card">
          <h3>{{ atvRefKind === 'threat' ? '新建威胁' : '新建脆弱性' }}</h3>
          <label class="fld">编码<input v-model="arf.code" :placeholder="atvRefKind === 'threat' ? 'T-PHISH' : 'V-WEAKPWD'" /></label>
          <label class="fld">名称<input v-model="arf.name" /></label>
          <label class="fld">分类<input v-model="arf.category" placeholder="如 人为/技术/环境" /></label>
          <label class="fld">所属组织<select v-model.number="arf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="atvErr" class="cerr">{{ atvErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="atvRefKind = null">取消</button>
            <button class="btn" :disabled="!arf.code || !arf.name || refSaving" @click="submitAtvRef">确认</button>
          </div>
        </div>
      </div>

      <!-- 处置决策弹窗（四选一 + RTP 落地要素，需求 4.5.3 / ISO 27001 RTP）-->
      <div v-if="treatTarget" class="modal-mask" @click.self="treatTarget = null">
        <div class="modal-card wide">
          <h3>处置决策与计划（RTP）</h3>
          <p class="muted" style="margin:-6px 0 14px">{{ treatTarget.title }}</p>
          <div class="fld-2col">
            <label class="fld">决策（四选一）
              <select v-model="tdf.decision">
                <option value="MITIGATE">降低（实施控制措施）</option>
                <option value="ACCEPT">接受（走管理层接受流程）</option>
                <option value="TRANSFER">转移（保险/外包）</option>
                <option value="AVOID">规避（停止相关活动）</option>
              </select>
            </label>
            <label class="fld">预期残余等级
              <select v-model="tdf.expectedResidual">
                <option :value="null">— 未定 —</option>
                <option value="VERY_LOW">极低</option><option value="LOW">低</option><option value="MID">中</option>
                <option value="HIGH">高</option><option value="VERY_HIGH">极高</option>
              </select>
            </label>
          </div>
          <label class="fld">处置措施<input v-model="tdf.plan" placeholder="如 强制改密+MFA / 购买网络安全险" /></label>
          <div class="fld-2col">
            <label class="fld">责任人<input v-model="tdf.owner" placeholder="如 张三" /></label>
            <label class="fld">完成期限<input type="date" v-model="tdf.dueDate" /></label>
          </div>
          <label class="fld">所需资源/预算<input v-model="tdf.resource" placeholder="如 安全预算 5 万、外部渗透团队 2 人周" /></label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="treatTarget = null">取消</button>
            <button class="btn" :disabled="!tdf.plan || refSaving" @click="submitTreat">确认处置</button>
          </div>
        </div>
      </div>

      <!-- ===== 参考库「新建」弹窗（定义 KRI / 定义控件 / 新建模板，按 refModal 切换）===== -->
      <div v-if="refModal" class="modal-mask" @click.self="refModal = null">
        <!-- 定义 KRI -->
        <div v-if="refModal === 'kri'" class="modal-card">
          <h3>{{ $t('risk.kri.newKri') }}</h3>
          <label class="fld">{{ $t('risk.ref.code') }}<input v-model="kriForm.code" placeholder="KRI-VULN-001" /></label>
          <label class="fld">{{ $t('risk.ref.name') }}<input v-model="kriForm.name" /></label>
          <label class="fld">{{ $t('risk.kri.f.unit') }}<input v-model="kriForm.unit" placeholder="个 / % / 天" /></label>
          <label class="fld">{{ $t('risk.kri.f.direction') }}
            <select v-model="kriForm.direction"><option value="UPPER_BAD">{{ $t('risk.kri.dir.UPPER_BAD') }}</option><option value="LOWER_BAD">{{ $t('risk.kri.dir.LOWER_BAD') }}</option></select>
          </label>
          <label class="fld">{{ $t('risk.kri.f.warn') }}<input v-model.number="kriForm.thresholdWarning" type="number" /></label>
          <label class="fld">{{ $t('risk.kri.f.crit') }}<input v-model.number="kriForm.thresholdCritical" type="number" /></label>
          <label class="fld">{{ $t('risk.ref.owner') }}<input v-model="kriForm.owner" /></label>
          <label class="fld">{{ $t('risk.ref.org') }}<select v-model.number="kriForm.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="refError" class="cerr">{{ refError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="refModal = null">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!kriForm.code || !kriForm.name || kriForm.thresholdWarning == null || kriForm.thresholdCritical == null || refSaving" @click="submitRef">{{ refSaving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
        <!-- 定义控件 -->
        <div v-else-if="refModal === 'control'" class="modal-card">
          <h3>{{ $t('risk.controls.newCtrl') }}</h3>
          <label class="fld">{{ $t('risk.ref.code') }}<input v-model="ctrlForm.code" placeholder="CTL-ACL-001" /></label>
          <label class="fld">{{ $t('risk.ref.name') }}<input v-model="ctrlForm.name" /></label>
          <label class="fld">{{ $t('risk.controls.f.domain') }}<input v-model="ctrlForm.domain" :placeholder="$t('risk.controls.f.domainPh')" /></label>
          <label class="fld">{{ $t('risk.ref.owner') }}<input v-model="ctrlForm.owner" /></label>
          <label class="fld">{{ $t('risk.ref.org') }}<select v-model.number="ctrlForm.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="refError" class="cerr">{{ refError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="refModal = null">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!ctrlForm.code || !ctrlForm.name || refSaving" @click="submitRef">{{ refSaving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
        <!-- 新建模板 -->
        <div v-else class="modal-card">
          <h3>{{ $t('risk.templates.newTpl') }}</h3>
          <label class="fld">{{ $t('risk.ref.code') }}<input v-model="tplForm.code" placeholder="TPL-MLPS-001" /></label>
          <label class="fld">{{ $t('risk.ref.name') }}<input v-model="tplForm.name" /></label>
          <label class="fld">{{ $t('risk.templates.f.framework') }}
            <select v-model="tplForm.framework"><option value="MLPS">{{ $t('risk.templates.fw.MLPS') }}</option><option value="ISO27001">{{ $t('risk.templates.fw.ISO27001') }}</option><option value="PCI_DSS">{{ $t('risk.templates.fw.PCI_DSS') }}</option><option value="PBOC">{{ $t('risk.templates.fw.PBOC') }}</option><option value="ISO27701">ISO 27701 隐私管理</option><option value="ISO20000">ISO 20000 服务管理</option><option value="ISO22301">ISO 22301 业务连续性</option><option value="PIPL">PIPL 个人信息保护</option></select>
          </label>
          <label class="fld">{{ $t('risk.templates.f.desc') }}<input v-model="tplForm.description" /></label>
          <label class="fld">{{ $t('risk.ref.owner') }}<input v-model="tplForm.owner" /></label>
          <label class="fld">{{ $t('risk.ref.org') }}<select v-model.number="tplForm.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="refError" class="cerr">{{ refError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="refModal = null">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!tplForm.code || !tplForm.name || refSaving" @click="submitRef">{{ refSaving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
      </div>
      <!-- 模板详情抽屉（R4 模板中心：条款预览 + 表单版本 + 两层关系说明）-->
      <div v-if="tplDetail" class="modal-mask" @click.self="tplDetail = null">
        <div class="modal-card wide" style="width:720px">
          <h3>{{ tplDetail.name }}<span class="pill" style="margin-left:8px">{{ tplDetail.code }}</span></h3>
          <p class="muted" style="margin:-8px 0 12px">{{ tplDetail.description || '—' }}</p>
          <!-- ① 完整模板样式预览（启用中的报告表单按章节/字段/明细表渲染）-->
          <div class="ch" style="padding:4px 0 4px"><h3 style="font-size:13px">模板样式预览</h3>
            <span class="sub">评估将按此结构填写；报告导出格式与此一致</span>
          </div>
          <div class="tpl-preview">
            <div v-if="tplItemsBusy" class="hint">加载中…</div>
            <template v-else-if="tplPreviewSchema">
              <div v-for="(sec, si) in tplPreviewSchema.sections" :key="si" class="tp-sec">
                <div class="tp-sec-title">{{ sec.title || ('章节 ' + (si + 1)) }}</div>
                <div v-for="f in sec.fields" :key="f.key" class="tp-field">
                  <span class="tp-label">{{ f.key }}</span>
                  <span class="tp-input" :class="f.type">{{ TP_TYPE[f.type] || f.type }}</span>
                </div>
                <div v-for="l in (sec.lists || [])" :key="l.key" class="tp-list">
                  <div class="tp-list-name">明细表 · {{ l.key }}</div>
                  <div class="tp-cols"><span v-for="c in l.columns" :key="c.key" class="tp-col">{{ c.key }}<i>{{ TP_TYPE[c.type] || c.type }}</i></span></div>
                </div>
              </div>
            </template>
            <div v-else class="hint">该模板尚无启用的报告表单——在下方「表单版本」上传 .docx 后启用。</div>
          </div>

          <!-- ② 表单版本管理（上传新版本/启用/下载）-->
          <div class="ch" style="padding:10px 0 4px"><h3 style="font-size:13px">表单版本</h3><span class="cnt">{{ tplDetailForms.length }}</span>
            <input type="file" accept=".docx" style="display:none" id="tpl-detail-docx" @change="onDetailDocxPicked" />
            <button v-if="canWriteRisk" class="btn ghost sm" style="margin-left:auto" :disabled="tplDetailBusy" @click="pickDetailDocx">{{ tplDetailBusy ? '处理中…' : '上传新版本 .docx' }}</button>
          </div>
          <div class="tpl-forms">
            <div v-for="f in tplDetailForms" :key="f.id" class="scope-row">
              <span class="code">v{{ f.versionNo }}</span><b>{{ f.name }}</b>
              <span class="st" :class="f.status === 'ACTIVE' ? 'ok' : 'wait'" style="margin-left:6px"><span class="d"></span>{{ f.status === 'ACTIVE' ? '已启用' : '草稿' }}</span>
              <button v-if="canWriteRisk && f.status !== 'ACTIVE'" class="mini-a" style="margin-left:auto;border-color:var(--accent)" @click="activateDetailForm(f)">启用</button>
              <a class="mini-a" :style="f.status === 'ACTIVE' ? 'margin-left:auto' : ''" :href="'/api/assessment-templates/forms/' + f.id + '/docx'" target="_blank">下载 .docx</a>
            </div>
            <div v-if="!tplDetailForms.length" class="hint">未挂报告表单——点右上「上传新版本 .docx」（占位符约定）。</div>
          </div>

          <!-- ③ 体系条款清单 -->
          <div class="ch" style="padding:10px 0 4px"><h3 style="font-size:13px">体系条款清单</h3><span class="cnt">{{ tplItems.length }}</span><span class="sub">定义评什么（评估范围对照）</span></div>
          <div class="tpl-items">
            <table v-if="tplItems.length" style="width:100%">
              <thead><tr><th style="width:40px">#</th><th style="width:110px">条款</th><th>要求</th></tr></thead>
              <tbody>
                <tr v-for="it in tplItems" :key="it.id">
                  <td class="num">{{ it.seq }}</td>
                  <td><span class="pill">{{ it.clause || '—' }}</span></td>
                  <td style="font-size:12.5px;line-height:1.6">{{ it.requirement }}</td>
                </tr>
              </tbody>
            </table>
            <div v-else class="hint">该模板暂无条款项。</div>
          </div>
          <p v-if="tplDetailErr" class="cerr">{{ tplDetailErr }}</p>
          <div class="modal-actions"><button class="btn ghost" @click="tplDetail = null">关闭</button></div>
        </div>
      </div>

      <!-- 克隆模板弹窗（R4）-->
      <div v-if="tplCloneTarget" class="modal-mask" @click.self="tplCloneTarget = null">
        <div class="modal-card">
          <h3>克隆模板 · {{ tplCloneTarget.name }}</h3>
          <p class="muted" style="margin:-6px 0 12px">复制元数据与全部条款项，新模板为草稿（可增改后发布）。</p>
          <label class="fld">新模板编码<input v-model="tplCloneForm.code" placeholder="如 TPL-MLPS-PAY" /></label>
          <label class="fld">新模板名称<input v-model="tplCloneForm.name" :placeholder="tplCloneTarget.name + '（副本）'" /></label>
          <label class="fld">目标组织
            <select v-model.number="tplCloneForm.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select>
          </label>
          <p v-if="tplCloneErr" class="cerr">{{ tplCloneErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="tplCloneTarget = null">取消</button>
            <button class="btn" :disabled="!tplCloneForm.code || tplCloneBusy" @click="submitTplClone">{{ tplCloneBusy ? '克隆中…' : '确认克隆' }}</button>
          </div>
        </div>
      </div>

      <!-- 场景生成发现弹窗（V48 · R2：识别→登记打通）-->
      <div v-if="toFindingTarget" class="modal-mask" @click.self="toFindingTarget = null">
        <div class="modal-card">
          <h3>场景生成风险发现</h3>
          <p class="muted" style="margin:-6px 0 12px">
            {{ assetNameOf(toFindingTarget.assetId) }} × {{ threatNameOf(toFindingTarget.threatId) }} × {{ vulnNameOf(toFindingTarget.vulnerabilityId) }}
            · 固有 {{ $t(riskLabel(toFindingTarget.inherentLevel)) }}
          </p>
          <label class="fld">纳入评估（同一评估同一场景只生成一次）
            <select v-model.number="toFindingAssessmentId">
              <option :value="0" disabled>— 选择进行中的评估 —</option>
              <option v-for="a in liveTasks.filter(x => x.status !== 'COMPLETED')" :key="a.id" :value="a.id">#{{ a.id }} · {{ a.title }}</option>
            </select>
          </label>
          <p v-if="toFindingErr" class="cerr">{{ toFindingErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="toFindingTarget = null">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!toFindingAssessmentId || toFindingBusy" @click="submitToFinding">{{ toFindingBusy ? $t('common.submitting') : '生成发现' }}</button>
          </div>
        </div>
      </div>

    </section>

    <!-- ============ 下钻视图：评估报告（固有/残余风险 + 管理层接受） ============ -->
    <section v-show="drill" class="view view-risk">
      <div class="phead">
        <div>
          <span class="bk" @click="backFromDrill">{{ $t('risk.report.back') }}</span>
          <div class="kqt">RA-{{ drillId }} · {{ $t('risk.tag') }}</div>
          <h1>{{ drillTitle || $t('risk.report.title') }}</h1>
        </div>
        <div class="sp"></div>
        <span v-if="exportError" class="cerr" style="margin-right: 10px">{{ exportError }}</span>
        <!-- 表单引擎 P3：回填上传模板 → 导出 Word/PDF（格式同官方模板，可交审计） -->
        <button class="btn ghost" @click="exportRtp">导出 RTP</button>
        <button class="btn ghost" :disabled="!!exporting" @click="exportReport('docx')">
          {{ exporting === 'docx' ? '导出中…' : '导出 Word' }}
        </button>
        <button class="btn" :disabled="!!exporting" @click="exportReport('pdf')">
          {{ exporting === 'pdf' ? '导出中…' : '导出 PDF' }}
        </button>
      </div>

      <!-- R1 · 报告第一节：评估背景（背景建立元数据，终态冻结）-->
      <div class="card" style="margin-bottom:14px">
        <div class="ch"><h3>一、评估背景</h3><span class="sub">范围 · 目的 · 依据 · 方法 · 准则（ISO 27005 / GB/T 20984 背景建立）</span>
          <button v-if="canWriteRisk && drillMeta && drillMeta.status !== 'COMPLETED'" class="btn ghost sm" style="margin-left:auto" @click="openCtxEdit">编辑背景</button>
        </div>
        <div class="cb ctxgrid" v-if="drillMeta">
          <div class="ctxrow"><span class="ck">评估范围</span><span class="cv">{{ drillMeta.scope || '—（未填写，编辑背景补齐）' }}</span></div>
          <div class="ctxrow"><span class="ck">评估目的</span><span class="cv">{{ drillMeta.objective || '—' }}</span></div>
          <div class="ctxrow"><span class="ck">依据标准</span><span class="cv"><template v-if="drillMeta.basis"><span v-for="b in drillMeta.basis.split(',')" :key="b" class="pill" style="margin-right:5px">{{ basisLabel(b) }}</span></template><template v-else>—</template></span></div>
          <div class="ctxrow"><span class="ck">方式方法</span><span class="cv"><template v-if="drillMeta.methods"><span v-for="m in drillMeta.methods.split(',')" :key="m" class="pill" style="margin-right:5px">{{ methodLabel(m) }}</span></template><template v-else>—</template></span></div>
          <div class="ctxrow"><span class="ck">评估准则</span><span class="cv">{{ drillMeta.criteria || '—' }}</span></div>
          <div class="ctxrow"><span class="ck">评估组</span><span class="cv">{{ drillMeta.team || '—' }}</span></div>
          <div class="ctxrow"><span class="ck">评估期间</span><span class="cv">{{ (drillMeta.startDate || '?') + ' ~ ' + (drillMeta.endDate || '?') }}</span></div>
        </div>
      </div>

      <!-- 编辑背景弹窗 -->
      <div v-if="showCtxEdit" class="modal-mask" @click.self="showCtxEdit = false">
        <div class="modal-card wide">
          <h3>编辑评估背景 · RA-{{ drillId }}</h3>
          <label class="fld">评估范围与边界<textarea v-model="ctxEdit.scope" rows="2"></textarea></label>
          <label class="fld">评估目的与背景<textarea v-model="ctxEdit.objective" rows="2"></textarea></label>
          <div class="fld">依据标准（多选）
            <div class="opt-grid"><label v-for="b in BASIS_OPTS" :key="b.v" class="opt" :class="{ on: ctxEdit.basis.includes(b.v) }"><input type="checkbox" :value="b.v" v-model="ctxEdit.basis" /><span>{{ b.l }}</span></label></div>
          </div>
          <div class="fld">方式方法（多选）
            <div class="opt-grid"><label v-for="m in METHOD_OPTS" :key="m.v" class="opt" :class="{ on: ctxEdit.methods.includes(m.v) }"><input type="checkbox" :value="m.v" v-model="ctxEdit.methods" /><span>{{ m.l }}</span></label></div>
          </div>
          <label class="fld">评估准则<textarea v-model="ctxEdit.criteria" rows="2"></textarea></label>
          <label class="fld">评估组成员<input v-model="ctxEdit.team" /></label>
          <div class="fld-2col">
            <label class="fld">开始日<input type="date" v-model="ctxEdit.startDate" /></label>
            <label class="fld">结束日<input type="date" v-model="ctxEdit.endDate" /></label>
          </div>
          <p v-if="ctxErr" class="cerr">{{ ctxErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showCtxEdit = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="ctxSaving" @click="saveCtxEdit">{{ ctxSaving ? $t('common.submitting') : '保存背景' }}</button>
          </div>
        </div>
      </div>

      <!-- R2 · 评估范围资产（GB/T 20984 资产识别：背景的"范围"落到具体资产清单）-->
      <div class="card" style="margin-bottom:14px">
        <div class="ch"><h3>二、范围资产清单</h3><span class="cnt">{{ scopeAssets.length }}</span><span class="sub">资产识别 · 与 A-T-V 场景联动</span>
          <div v-if="canWriteRisk && drillMeta && drillMeta.status !== 'COMPLETED'" style="margin-left:auto;display:flex;gap:8px">
            <select class="selmini" v-model.number="scopeAddId">
              <option :value="0" disabled>— 选择资产 —</option>
              <option v-for="a in atvAssets.filter(x => !scopeAssets.some(s => s.assetId === x.id))" :key="a.id" :value="a.id">{{ a.name }}</option>
            </select>
            <button class="btn ghost sm" :disabled="!scopeAddId" @click="addScopeAsset">纳入范围</button>
          </div>
        </div>
        <div class="cb" style="padding-top:0">
          <div v-for="s in scopeAssets" :key="s.id" class="scope-row">
            <span class="code">A-{{ s.assetId }}</span><b>{{ s.assetName }}</b>
            <span class="pill" v-if="s.assetType">{{ s.assetType }}</span>
            <span class="muted" style="margin-left:auto">{{ s.addedBy }}</span>
            <button v-if="canWriteRisk && drillMeta && drillMeta.status !== 'COMPLETED'" class="mini-x" title="移出范围" @click="removeScopeAsset(s)">✕</button>
          </div>
          <div v-if="!scopeAssets.length" class="emptyrow">暂无范围资产——右上选择资产「纳入范围」，或在 A-T-V 建模页从场景生成发现。</div>
        </div>
      </div>

      <!-- R3 · 过程文档中心（GB/T 20984 过程文档：计划书/访谈记录等上传件 + 系统生成件导出）-->
      <div class="card" style="margin-bottom:14px">
        <div class="ch"><h3>过程文档</h3><span class="cnt">{{ assessDocs.length }}</span><span class="sub">计划书 / 访谈记录等上传留档 · 报告与 RTP 由右上按钮即时生成</span>
          <div v-if="canWriteRisk" style="margin-left:auto;display:flex;gap:8px;align-items:center">
            <select class="selmini" v-model="docUp.docType">
              <option value="PLAN">评估计划书</option><option value="INTERVIEW">访谈记录</option>
              <option value="ACCEPTANCE">接受声明</option><option value="OTHER">其他</option>
            </select>
            <input type="file" style="display:none" id="assess-doc-file" @change="onDocFile" />
            <button class="btn ghost sm" @click="pickDocFile">＋ 上传文档</button>
          </div>
        </div>
        <div class="cb" style="padding-top:0">
          <div v-for="d in assessDocs" :key="d.id" class="scope-row">
            <span class="pill">{{ DOC_TYPE_LABEL[d.docType] || d.docType }}</span>
            <b>{{ d.name }}</b>
            <span class="muted">{{ d.fileName }} · {{ (d.sizeBytes / 1024).toFixed(1) }}KB · {{ d.uploadedBy }}</span>
            <span class="muted" :title="d.sha256" style="margin-left:auto">{{ (d.sha256 || '').slice(0, 10) }}…</span>
            <a class="mini-a" :href="'/api/assessment-docs/' + d.id + '/download'" target="_blank">下载</a>
            <button v-if="canWriteRisk" class="mini-x" title="删除" @click="deleteDoc(d)">✕</button>
          </div>
          <div v-if="!assessDocs.length" class="emptyrow">暂无过程文档——上传评估计划书/访谈记录等留档（sha256 固化防篡改）。</div>
        </div>
      </div>

      <!-- 表单引擎 P1：按模板 .docx 解析出的规范评估表单（真实后端，可填写保存） -->
      <!-- 七轮 7-13（A28）：范围资产/场景变化后可从系统数据重新预填三张明细表（覆盖三张系统清单，其余手填保留） -->
      <div v-if="canWriteRisk" style="display:flex; justify-content:flex-end; margin: 6px 0 -6px">
        <button class="mini" :disabled="reprefillBusy" @click="doReprefill">{{ reprefillBusy ? '同步中…' : '⟳ 从系统数据重新预填明细表' }}</button>
      </div>
      <AssessmentFormFill :key="formRefreshKey" :assessment-id="drillId" @saved="onFormSaved" />

      <!-- 表单引擎 P2：整体残余等级 + 管理层接受签批（CR-002 完成门控） -->
      <AssessmentSignoff :assessment-id="drillId" ref="signoffRef" />

      <!-- 评估 KPI 四卡（真值：由本评估的风险发现实时计算）-->
      <div class="kpibar k4">
        <div class="kc">
          <div class="l">整体残余等级</div>
          <div class="v" :style="{ color: drillMeta && (drillMeta.riskLevel === 'HIGH' || drillMeta.riskLevel === 'VERY_HIGH') ? 'var(--danger)' : 'var(--text-1)' }">
            {{ drillMeta && drillMeta.riskLevel ? $t(riskLabel(drillMeta.riskLevel)) : '—' }}
          </div>
          <div class="s">打分聚合（残余最高档）</div>
        </div>
        <div class="kc">
          <div class="l">风险发现</div>
          <div class="v">{{ findings.length }}</div>
        </div>
        <div class="kc">
          <div class="l">高/极高残余</div>
          <div class="v" style="color: var(--danger)">{{ drillHigh }}</div>
        </div>
        <div class="kc">
          <div class="l">待处置</div>
          <div class="v">{{ drillOpen }}</div>
        </div>
      </div>

      <!-- 残余等级分布（真值：本评估发现按 残余优先/无残余取固有 统计）-->
      <div class="card" style="margin-bottom:14px" v-if="findings.length">
        <div class="ch"><h3>{{ $t('risk.report.donut.title') }}</h3><span class="sub">真值 · 残余优先，无残余取固有</span></div>
        <div class="cb">
          <div class="exbars" style="max-width:520px">
            <div v-for="b in drillLevelDist" :key="b.l" class="exbar-row">
              <span class="exl" style="width:40px">{{ b.l }}</span>
              <div class="extrack"><i :style="{ width: b.w, background: b.c }"></i></div>
              <b>{{ b.v }}</b>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 风险发现 · 关闭门控（CR-002 红线，真实后端 GET /api/risk-findings）=====
           残余高/极高且无有效风险接受 → 关闭按钮禁用 + 标明原因；登记风险接受后放行。
           后端 RiskFindingService.assertClosable 强制同样规则（纵深防御）。 -->
      <div class="card">
        <div class="ch">
          <h3>{{ $t('risk.gate.title') }}</h3>
          <span class="redline-badge">{{ $t('risk.gate.badge') }}</span>
        </div>
        <div class="cb" style="overflow-x: auto; padding-bottom: 6px">
          <table style="min-width: 780px">
            <thead>
              <tr>
                <th>{{ $t('risk.gate.th.finding') }}</th>
                <th>{{ $t('risk.gate.th.inherent') }}</th>
                <th>{{ $t('risk.gate.th.residual') }}</th>
                <th>{{ $t('risk.gate.th.acceptance') }}</th>
                <th>{{ $t('risk.gate.th.status') }}</th>
                <th>{{ $t('risk.gate.th.ops') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="f in findings" :key="f.id">
                <td><span class="code">#{{ f.id }}</span> {{ f.title }}</td>
                <td>
                  <span v-if="f.inherentLevel" class="tag" :class="riskCls(f.inherentLevel)">{{ $t(riskLabel(f.inherentLevel)) }}</span>
                  <span v-else>—</span>
                </td>
                <td>
                  <span v-if="f.residualLevel" class="tag" :class="riskCls(f.residualLevel)">{{ $t(riskLabel(f.residualLevel)) }}</span>
                  <span v-else>—</span>
                </td>
                <td>
                  <span v-if="f.riskAcceptanceId" class="st ok"><span class="d"></span>#{{ f.riskAcceptanceId }}</span>
                  <span v-else-if="isGated(f)" class="st over" :title="$t('risk.gate.gatedTip')"><span class="d"></span>{{ $t('risk.gate.missing') }}</span>
                  <span v-else>—</span>
                </td>
                <td><span class="st" :class="findingStCls(f.status)"><span class="d"></span>{{ $t('risk.gate.fstatus.' + f.status) }}</span></td>
                <td class="ops">
                  <!-- 处置决策四选一（需求 4.5.3：降低/接受/转移/规避）：仅 OPEN 可录入 -->
                  <button v-if="f.status === 'OPEN'" class="btn ghost sm" :disabled="busyId === f.id" @click="openTreat(f)">处置</button>
                  <span v-else-if="f.treatmentDecision" class="pill" :title="f.treatmentPlan">{{ TD_LABEL[f.treatmentDecision] || f.treatmentDecision }}</span>
                  <!-- 风险接受（A5 审批化两步）：被门控且未申请 → 申请；已申请待审批 → 审批通过/驳回。
                       (待审批态本会期内用 requestedIds 跟踪；Phase D 由后端暴露 PENDING 态持久化) -->
                  <template v-if="isGated(f)">
                    <button v-if="!requestedIds.has(f.id)" class="btn ghost sm" @click="openAccept(f)">{{ $t('risk.gate.requestAccept') }}</button>
                    <template v-else>
                      <button class="btn sm" :disabled="busyId === f.id" @click="approveAccept(f)">{{ $t('risk.gate.approveAccept') }}</button>
                      <button class="btn ghost sm" :disabled="busyId === f.id" @click="rejectAccept(f)">{{ $t('risk.gate.rejectAccept') }}</button>
                    </template>
                  </template>
                  <!-- 关闭：OPEN/IN_TREATMENT → DONE；被门控时禁用 + 原因提示 -->
                  <button
                    v-if="f.status === 'OPEN' || f.status === 'IN_TREATMENT'"
                    class="btn sm"
                    :disabled="isGated(f) || busyId === f.id"
                    :title="isGated(f) ? $t('risk.gate.gatedTip') : ''"
                    @click="closeFinding(f, false)"
                  >{{ $t('risk.gate.close') }}</button>
                  <!-- 验证：DONE → VERIFIED -->
                  <button
                    v-else-if="f.status === 'DONE'"
                    class="btn ghost sm"
                    :disabled="busyId === f.id"
                    @click="closeFinding(f, true)"
                  >{{ $t('risk.gate.verify') }}</button>
                  <span v-else-if="f.status === 'VERIFIED'" class="muted">{{ $t('risk.gate.verified') }}</span>
                </td>
              </tr>
              <tr v-if="!findings.length">
                <td colspan="6" class="emptyrow">{{ findingsError || $t('risk.gate.empty') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="opError" class="cerr" style="padding: 0 16px 12px">{{ opError }}</p>
      </div>

      <!-- 申请风险接受弹窗（A5：POST /request-acceptance 登记 PENDING + 启动审批；审批通过才回填、解除门控）-->
      <div v-if="showAccept" class="modal-mask" @click.self="showAccept = false">
        <div class="modal-card">
          <h3>{{ $t('risk.gate.acceptTitle') }}</h3>
          <p class="muted" style="margin: -6px 0 14px; font-size: 12.5px">{{ acceptTarget && acceptTarget.title }}</p>
          <label class="fld">{{ $t('risk.gate.reason') }}
            <input v-model="acceptForm.reason" :placeholder="$t('risk.gate.reasonPh')" />
          </label>
          <p v-if="opError" class="cerr">{{ opError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAccept = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!acceptForm.reason || saving" @click="submitAccept">
              {{ saving ? $t('common.submitting') : $t('risk.gate.acceptConfirm') }}
            </button>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import AssessmentFormFill from '@/components/AssessmentFormFill.vue'
import AssessmentSignoff from '@/components/AssessmentSignoff.vue'
import { api } from '@/api/client.js'
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()
import { canWrite } from '@/auth.js'
import { exportCsv } from '@/utils/csv.js'

// ---- Tab 切换 ----
const tabs = ['tasks', 'register', 'templates', 'controls', 'kri', 'atv']
const activeTab = ref('tasks')

// ---- 下钻：评估报告视图开关（点击任务行进入，← 返回）----
const drill = ref(false)

// 表单引擎 P2：保存表单后刷新签批面板的整体残余等级；并刷新任务列表的风险等级列
const signoffRef = ref(null)
async function onFormSaved() {
  if (signoffRef.value) signoffRef.value.reload()
  try { liveTasks.value = await api.get('/assessments') } catch (e) { /* 忽略 */ }
}

// 表单引擎 P3：导出回填后的报告（docx/pdf），按附件下载
const exporting = ref('')
const exportError = ref('')
async function exportReport(fmt) {
  exporting.value = fmt
  exportError.value = ''
  try {
    const resp = await fetch('/api/assessments/' + drillId.value + '/report.' + fmt, { credentials: 'include' })
    if (!resp.ok) {
      let msg = 'HTTP ' + resp.status
      try { const j = await resp.json(); msg = j.message || msg } catch (e) { /* 非 JSON */ }
      throw new Error(msg)
    }
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'risk-assessment-' + drillId.value + '.' + fmt
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    exportError.value = '导出失败：' + e.message
  } finally {
    exporting.value = ''
  }
}

// ---- 下钻：进入某评估 → 拉取其风险发现（真实后端）+ CR-002 关闭门控 ----
const drillId = ref(null)
const drillTitle = ref('')
const findings = ref([])
const findingsError = ref('')
const opError = ref('') // 关闭/接受等写操作的错误（含后端门控 409 消息）
const busyId = ref(null) // 正在流转的 finding id，避免重复点击

const route = useRoute()
const router = useRouter()

async function drillInto(r) {
  drill.value = true
  drillId.value = r.id
  drillTitle.value = r.title
  // 架构治理包 A14：把评估 id 写进 URL query，支持深链/刷新保持/收藏分享（#/risk?a=1013）
  if (String(route.query.a) !== String(r.id)) {
    router.replace({ query: { ...route.query, a: r.id } })
  }
  loadDrillMeta()
  loadScopeAssets()
  loadAssessDocs()
  await loadFindings()
}

/** A14：从 URL 深链恢复下钻（挂载时 + query 变化时）。 */
async function restoreDrillFromRoute() {
  const a = route.query.a
  if (a && (!drill.value || String(drillId.value) !== String(a))) {
    const t = liveTasks.value.find((x) => String(x.id) === String(a))
    if (t) await drillInto(t)
  } else if (!a && drill.value) {
    drill.value = false
    drillId.value = null
  }
}

/** A14：返回列表——清 URL query（浏览器后退/前进也能驱动 drill 开合）。 */
function backFromDrill() {
  drill.value = false
  drillId.value = null
  const q = { ...route.query }
  delete q.a
  router.replace({ query: q })
}

// ---- R2 · 场景生成发现 + 评估范围资产 ----
const toFindingTarget = ref(null)
const toFindingAssessmentId = ref(0)
const toFindingBusy = ref(false)
const toFindingErr = ref('')
function openToFinding(s) { toFindingTarget.value = s; toFindingAssessmentId.value = 0; toFindingErr.value = '' }
async function submitToFinding() {
  toFindingBusy.value = true; toFindingErr.value = ''
  try {
    await api.post('/risk-scenarios/' + toFindingTarget.value.id + '/to-finding', { assessmentId: toFindingAssessmentId.value })
    toFindingTarget.value = null
  } catch (e) { toFindingErr.value = e.message } finally { toFindingBusy.value = false }
}

const scopeAssets = ref([])
const scopeAddId = ref(0)
async function loadScopeAssets() {
  try { scopeAssets.value = await api.get('/assessments/' + drillId.value + '/assets') } catch (e) { scopeAssets.value = [] }
}
async function addScopeAsset() {
  try {
    await api.post('/assessments/' + drillId.value + '/assets', { assetId: scopeAddId.value })
    scopeAddId.value = 0; await loadScopeAssets()
  } catch (e) { opError.value = e.message }
}
async function removeScopeAsset(s) {
  try { await api.del('/assessments/' + drillId.value + '/assets/' + s.id); await loadScopeAssets() }
  catch (e) { opError.value = e.message }
}

// ---- R4 · 模板中心：完整样式预览 + 版本管理 + 克隆 ----
const TP_TYPE = { text: '单行文本', textarea: '多行文本', date: '日期', number: '数值', score: '打分 1-5', level: '五级等级', select: '下拉选择' }
const tplDetail = ref(null)
const tplItems = ref([])
const tplItemsBusy = ref(false)
const tplDetailForms = ref([])
const tplPreviewSchema = ref(null)
const tplDetailBusy = ref(false)
const tplDetailErr = ref('')
async function openTplDetail(t) {
  tplDetail.value = t
  tplItems.value = []; tplDetailForms.value = []; tplPreviewSchema.value = null
  tplDetailErr.value = ''; tplItemsBusy.value = true
  try {
    const [items, forms] = await Promise.all([
      api.get('/assessment-templates/' + t.id + '/items').catch(() => []),
      api.get('/assessment-templates/' + t.id + '/forms').catch(() => [])
    ])
    tplItems.value = items; tplDetailForms.value = forms
    // 完整样式预览：取启用中的表单 schema 渲染
    const active = forms.find((f) => f.status === 'ACTIVE')
    if (active) {
      const v = await api.get('/assessment-templates/forms/' + active.id + '/schema').catch(() => null)
      tplPreviewSchema.value = v && v.schema ? v.schema : null
    }
  } finally { tplItemsBusy.value = false }
}
// 版本管理：上传新版本 / 启用（原模板卡上的上传能力移到这里）
function pickDetailDocx() { document.getElementById('tpl-detail-docx').click() }
async function onDetailDocxPicked(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file || !tplDetail.value) return
  tplDetailBusy.value = true; tplDetailErr.value = ''
  try {
    const fd = new FormData()
    fd.append('file', file)
    await api.upload('/assessment-templates/' + tplDetail.value.id + '/form', fd)
    await openTplDetail(tplDetail.value)
    await loadTplForms(tplDetail.value.id)
  } catch (err) { tplDetailErr.value = err.message } finally { tplDetailBusy.value = false }
}
async function activateDetailForm(f) {
  tplDetailErr.value = ''
  try {
    await api.post('/assessment-templates/forms/' + f.id + '/activate', {})
    await openTplDetail(tplDetail.value)
    await loadTplForms(tplDetail.value.id)
  } catch (e) { tplDetailErr.value = e.message }
}
const tplCloneTarget = ref(null)
const tplCloneForm = reactive({ code: '', name: '', orgId: 12 })
const tplCloneBusy = ref(false)
const tplCloneErr = ref('')
function openTplClone(t) {
  tplCloneTarget.value = t
  Object.assign(tplCloneForm, { code: t.code + '-COPY', name: '', orgId: 12 })
  tplCloneErr.value = ''
}
async function submitTplClone() {
  tplCloneBusy.value = true; tplCloneErr.value = ''
  try {
    await api.post('/assessment-templates/' + tplCloneTarget.value.id + '/clone', {
      orgId: tplCloneForm.orgId, code: tplCloneForm.code, name: tplCloneForm.name || null
    })
    tplCloneTarget.value = null
    await loadTemplates()
  } catch (e) { tplCloneErr.value = e.message } finally { tplCloneBusy.value = false }
}

// ---- R3 · 过程文档中心 + RTP 导出 ----
const DOC_TYPE_LABEL = { PLAN: '计划书', INTERVIEW: '访谈记录', REPORT: '报告', RTP: '处置计划', ACCEPTANCE: '接受声明', OTHER: '其他' }
const assessDocs = ref([])
const docUp = reactive({ docType: 'PLAN' })
async function loadAssessDocs() {
  try { assessDocs.value = await api.get('/assessments/' + drillId.value + '/docs') } catch (e) { assessDocs.value = [] }
}
function pickDocFile() { document.getElementById('assess-doc-file').click() }
async function onDocFile(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('docType', docUp.docType)
    fd.append('name', file.name.replace(/\.[^.]+$/, ''))
    await api.upload('/assessments/' + drillId.value + '/docs', fd)
    await loadAssessDocs()
  } catch (err) { opError.value = err.message }
}
async function deleteDoc(d) {
  if (!window.confirm(`确认删除过程文档「${d.name}」？`)) return
  try { await api.del('/assessment-docs/' + d.id); await loadAssessDocs() } catch (e) { opError.value = e.message }
}
function exportRtp() { window.open('/api/assessments/' + drillId.value + '/rtp.docx', '_blank') }

// ---- R1 · 评估背景（背景建立元数据展示与编辑）----
const drillMeta = ref(null)
const showCtxEdit = ref(false)
const ctxSaving = ref(false)
const ctxErr = ref('')
const ctxEdit = reactive({ scope: '', objective: '', basis: [], methods: [], criteria: '', team: '', startDate: '', endDate: '' })
const basisLabel = (v) => (BASIS_OPTS.find((b) => b.v === v.trim()) || { l: v }).l
const methodLabel = (v) => (METHOD_OPTS.find((m) => m.v === v.trim()) || { l: v }).l
async function loadDrillMeta() {
  drillMeta.value = null
  try { drillMeta.value = await api.get('/assessments/' + drillId.value) } catch (e) { /* 保持空 */ }
}
function openCtxEdit() {
  const m = drillMeta.value || {}
  Object.assign(ctxEdit, {
    scope: m.scope || '', objective: m.objective || '',
    basis: m.basis ? m.basis.split(',') : [], methods: m.methods ? m.methods.split(',') : [],
    criteria: m.criteria || DEFAULT_CRITERIA, team: m.team || '',
    startDate: m.startDate || '', endDate: m.endDate || ''
  })
  ctxErr.value = ''
  showCtxEdit.value = true
}
async function saveCtxEdit() {
  ctxSaving.value = true; ctxErr.value = ''
  try {
    await api.put('/assessments/' + drillId.value + '/context', {
      scope: ctxEdit.scope || null, objective: ctxEdit.objective || null,
      basis: ctxEdit.basis.join(',') || null, methods: ctxEdit.methods.join(',') || null,
      criteria: ctxEdit.criteria || null, team: ctxEdit.team || null,
      startDate: ctxEdit.startDate || null, endDate: ctxEdit.endDate || null
    })
    showCtxEdit.value = false
    await loadDrillMeta()
  } catch (e) { ctxErr.value = e.message } finally { ctxSaving.value = false }
}
async function loadFindings() {
  findingsError.value = ''
  opError.value = ''
  try {
    findings.value = await api.get('/risk-findings?assessmentId=' + drillId.value)
  } catch (e) {
    findingsError.value = e.message
    findings.value = []
  }
}

// 关闭门控判定（与后端 RiskFindingService.assertClosable 一致）：
// 残余 HIGH/VERY_HIGH 且无有效风险接受凭据 → 关闭被拦截。前端据此禁用按钮并标因。
function isGated(f) {
  return (f.residualLevel === 'HIGH' || f.residualLevel === 'VERY_HIGH') && !f.riskAcceptanceId
}
const FINDING_STATUS_CLS = { OPEN: 'wait', IN_TREATMENT: 'doing', DONE: 'ok', VERIFIED: 'ok' }
const findingStCls = (s) => FINDING_STATUS_CLS[s] || 'wait'

// 关闭/验证流转：POST /close?verify=…。即便前端放行，后端仍强制门控；
// 若被拦截（409 RISK_CLOSE_GATE）则把后端消息显示出来（纵深防御、红线可见）。
async function closeFinding(f, verify) {
  busyId.value = f.id
  opError.value = ''
  try {
    await api.post('/risk-findings/' + f.id + '/close?verify=' + verify, {})
    await loadFindings()
  } catch (e) {
    opError.value = e.message
  } finally {
    busyId.value = null
  }
}

// ---- 风险接受（A5 审批化两步：申请 → 审批通过才回填放行凭据、解除门控）----
const showAccept = ref(false)
const acceptTarget = ref(null)
const acceptForm = reactive({ reason: '' })
// 本会期内已提交申请、待审批的 finding id（Phase D 由后端暴露 PENDING 态后可去掉本地跟踪）
const requestedIds = ref(new Set())

function openAccept(f) {
  acceptTarget.value = f
  acceptForm.reason = ''
  opError.value = ''
  showAccept.value = true
}
/** 申请风险接受：POST /request-acceptance（PENDING，不放行）。 */
async function submitAccept() {
  saving.value = true
  opError.value = ''
  try {
    await api.post('/risk-findings/' + acceptTarget.value.id + '/request-acceptance', { reason: acceptForm.reason })
    const s = new Set(requestedIds.value)
    s.add(acceptTarget.value.id)
    requestedIds.value = s // 重新赋值触发响应式
    showAccept.value = false
    await loadFindings()
  } catch (e) {
    opError.value = e.message
  } finally {
    saving.value = false
  }
}
/** 审批通过：POST /accept-approve → 回填 riskAcceptanceId、门控解除。 */
async function approveAccept(f) {
  busyId.value = f.id
  opError.value = ''
  try {
    await api.post('/risk-findings/' + f.id + '/accept-approve', {})
    await loadFindings()
    clearRequested(f.id)
  } catch (e) {
    opError.value = e.message
  } finally {
    busyId.value = null
  }
}
/** 审批驳回：POST /accept-reject → 不放行（门控保持）。 */
async function rejectAccept(f) {
  busyId.value = f.id
  opError.value = ''
  try {
    await api.post('/risk-findings/' + f.id + '/accept-reject', {})
    await loadFindings()
    clearRequested(f.id)
  } catch (e) {
    opError.value = e.message
  } finally {
    busyId.value = null
  }
}
function clearRequested(id) {
  const s = new Set(requestedIds.value)
  s.delete(id)
  requestedIds.value = s
}

// ---- Tab1：评估任务表（真实后端数据 GET /api/assessments，非静态假数据）----
// 字段以后端为准：后端现有 id/title/riskLevel/status；模板/进度/截止等列后端暂无
// 对应字段（DM-5 C 类缺口），先以「—」占位、不臆造，待后端补字段后再填。
const liveTasks = ref([])
const loadError = ref('')

// ---- Tab2/3/4 参考库：真实后端数据（模板库 / 统一控件库 / KRI 监控）----
const templates = ref([])      // GET /api/assessment-templates
const controls = ref([])       // GET /api/controls
const ctrlMappings = ref({})   // {controlId: [{framework,clause}]} —— 控件的多框架覆盖（GET /{id}/mappings）
const kris = ref([])           // GET /api/kris

async function loadTemplates() {
  try {
    templates.value = await api.get('/assessment-templates')
    // 并行拉取每个模板的表单版本（表单引擎 P1：展示「已配置/未配置表单」+ 启用态）
    templates.value.forEach((t) => loadTplForms(t.id))
  } catch (e) { templates.value = [] }
}

// ---- A13：评估任务列表三列真值辅助 ----
/** 按模板 id 反查名称（任务行「体系模板」列）。 */
function tplName(id) {
  if (!id) return '—'
  const t = templates.value.find((x) => x.id === id)
  return t ? t.name : '#' + id
}
/** 按生命周期状态给出粗粒度进度（无逐题统计接口，以状态机阶段映射）。 */
function taskProgress(status) {
  return { DRAFT: '0%', IN_PROGRESS: '40%', PENDING_REVIEW: '80%', COMPLETED: '100%', CANCELLED: '—' }[status] || '—'
}

// ---- 表单引擎 P1：每个模板的 .docx 表单版本（上传/启用）----
const tplForms = reactive({})   // { [templateId]: { versions:[], active:form|null, busy, error, parsed } }
function tplFormState(tid) {
  if (!tplForms[tid]) tplForms[tid] = { versions: [], active: null, busy: false, error: '', parsed: null }
  return tplForms[tid]
}
async function loadTplForms(tid) {
  const st = tplFormState(tid)
  try {
    const list = await api.get('/assessment-templates/' + tid + '/forms')
    st.versions = list
    st.active = list.find((f) => f.status === 'ACTIVE') || null
  } catch (e) { st.error = e.message }
}
// 触发隐藏 file input
function pickDocx(tid) {
  const el = document.getElementById('docx-' + tid)
  if (el) el.click()
}
async function onDocxPicked(tid, ev) {
  const file = ev.target.files && ev.target.files[0]
  ev.target.value = ''  // 允许重复选同名文件
  if (!file) return
  const st = tplFormState(tid)
  st.busy = true; st.error = ''; st.parsed = null
  try {
    const fd = new FormData()
    fd.append('file', file)
    const form = await api.upload('/assessment-templates/' + tid + '/form', fd)
    // 统计解析出的字段/明细表数，给即时反馈
    let fields = 0, lists = 0
    for (const s of (form.schema?.sections || [])) { fields += (s.fields || []).length; lists += (s.lists || []).length }
    st.parsed = { id: form.id, versionNo: form.versionNo, sections: (form.schema?.sections || []).length, fields, lists }
    await loadTplForms(tid)
  } catch (e) {
    st.error = e.message
  } finally {
    st.busy = false
  }
}
async function activateTplForm(tid, formId) {
  const st = tplFormState(tid)
  st.busy = true; st.error = ''
  try {
    await api.post('/assessment-templates/forms/' + formId + '/activate', {})
    await loadTplForms(tid)
  } catch (e) { st.error = e.message } finally { st.busy = false }
}
const canWriteRisk = canWrite('risk')
async function loadControls() {
  try {
    controls.value = await api.get('/controls')
    // 逐控件取多框架映射（演示库规模小，N+1 可接受；覆盖体系 pill + 复用数据此）
    const entries = await Promise.all(
      controls.value.map((c) => api.get('/controls/' + c.id + '/mappings').then((m) => [c.id, m]).catch(() => [c.id, []]))
    )
    ctrlMappings.value = Object.fromEntries(entries)
    // B20：逐控件取当前可复用测试结论（有效且未过期的 EFFECTIVE），无则 null
    const reuse = await Promise.all(
      controls.value.map((c) => api.get('/controls/' + c.id + '/reusable-test').then((t) => [c.id, t || null]).catch(() => [c.id, null]))
    )
    ctrlReusable.value = Object.fromEntries(reuse)
  } catch (e) { controls.value = []; ctrlMappings.value = {}; ctrlReusable.value = {} }
}
// B20 控件测试复用：可复用结论 + 记录测试弹窗
const ctrlReusable = ref({})   // {controlId: reusableTest|null}
const showCtrlTest = ref(false)
const ctrlTestErr = ref('')
const ctlt = reactive({ controlId: null, controlName: '', testType: 'OPERATING', result: 'EFFECTIVE', validUntil: '', note: '' })
function openCtrlTest(c) {
  Object.assign(ctlt, { controlId: c.id, controlName: c.name, testType: 'OPERATING', result: 'EFFECTIVE', validUntil: '', note: '' })
  ctrlTestErr.value = ''; showCtrlTest.value = true
}
async function submitCtrlTest() {
  refSaving.value = true; ctrlTestErr.value = ''
  try {
    await api.post('/controls/' + ctlt.controlId + '/tests', {
      testType: ctlt.testType, result: ctlt.result, validUntil: ctlt.validUntil || null, note: ctlt.note || null
    })
    showCtrlTest.value = false; await loadControls()
  } catch (e) { ctrlTestErr.value = e.message } finally { refSaving.value = false }
}
async function loadKris() {
  try { kris.value = await api.get('/kris') } catch (e) { kris.value = [] }
}

// ===== M2 周边：评估计划管理（需求 4.2.1）=====
const plans = ref([])
const PT_LABEL = { ANNUAL: '年度', QUARTERLY: '季度', ADHOC: '临时专项' }
const PLST_LABEL = { PLANNED: '已排期', STARTED: '已启动', DONE: '已完成' }
const PLST_CLS = { PLANNED: 'wait', STARTED: 'doing', DONE: 'ok' }
async function loadPlans() { try { plans.value = await api.get('/assessment-plans') } catch (e) { plans.value = [] } }
const showPlan = ref(false)
const plf = reactive({ title: '', periodType: 'ANNUAL', plannedDate: '', templateId: null, orgId: 12 })
function openPlan() { Object.assign(plf, { title: '', periodType: 'ANNUAL', plannedDate: '', templateId: null, orgId: 12 }); opError.value = ''; showPlan.value = true }
async function submitPlan() {
  refSaving.value = true; opError.value = ''
  try {
    await api.post('/assessment-plans', { orgId: plf.orgId, title: plf.title, periodType: plf.periodType, plannedDate: plf.plannedDate || null, templateId: plf.templateId })
    showPlan.value = false; await loadPlans()
  } catch (e) { opError.value = e.message } finally { refSaving.value = false }
}
async function startPlan(pl) {
  opError.value = ''
  try { await api.post('/assessment-plans/' + pl.id + '/start', {}); await loadPlans(); liveTasks.value = await api.get('/assessments') }
  catch (e) { opError.value = e.message }
}
async function donePlan(pl) {
  opError.value = ''
  try { await api.post('/assessment-plans/' + pl.id + '/done', {}); await loadPlans() } catch (e) { opError.value = e.message }
}

// ===== M2 周边：A-T-V 建模（后端 /api/threats|vulnerabilities|risk-scenarios）=====
const threats = ref([])
const vulns = ref([])
const scenarios = ref([])
const atvAssets = ref([])
const atvErr = ref('')
async function loadAtv() {
  try {
    const [t2, v2, s2, a2] = await Promise.all([
      api.get('/threats'), api.get('/vulnerabilities'), api.get('/risk-scenarios'), api.get('/assets')
    ])
    threats.value = t2; vulns.value = v2; scenarios.value = s2; atvAssets.value = a2
  } catch (e) { atvErr.value = e.message }
}
const threatNameOf = (id) => (threats.value.find((x) => x.id === id) || {}).name || '#' + id
const vulnNameOf = (id) => (vulns.value.find((x) => x.id === id) || {}).name || '#' + id
const assetNameOf = (id) => (atvAssets.value.find((x) => x.id === id) || {}).name || '#' + id
const showScenario = ref(false)
const scf = reactive({ assetId: 0, threatId: 0, vulnerabilityId: 0, likelihood: 3, impact: 3, description: '' })
function openScenario() { Object.assign(scf, { assetId: 0, threatId: 0, vulnerabilityId: 0, likelihood: 3, impact: 3, description: '' }); atvErr.value = ''; showScenario.value = true }
async function submitScenario() {
  refSaving.value = true; atvErr.value = ''
  try {
    const a = atvAssets.value.find((x) => x.id === scf.assetId)
    await api.post('/risk-scenarios', { orgId: a ? a.orgId : 12, assetId: scf.assetId, threatId: scf.threatId, vulnerabilityId: scf.vulnerabilityId, likelihood: scf.likelihood, impact: scf.impact, description: scf.description || null })
    showScenario.value = false; await loadAtv()
  } catch (e) { atvErr.value = e.message } finally { refSaving.value = false }
}
// B44 漏扫导入：粘贴 CSV 行 → 解析为条目 → 批量导入（后端按 code 去重）
const showScanImport = ref(false)
const scanForm = reactive({ orgId: 12, text: '' })
const scanResult = ref(null)
function openScanImport() { Object.assign(scanForm, { orgId: 12, text: '' }); scanResult.value = null; atvErr.value = ''; showScanImport.value = true }
async function submitScanImport() {
  refSaving.value = true; atvErr.value = ''; scanResult.value = null
  try {
    const items = scanForm.text.split('\n').map((line) => {
      const [code, name, category, description] = line.split(',').map((x) => (x || '').trim())
      return { code, name, category: category || null, description: description || null }
    }).filter((it) => it.code || it.name)
    scanResult.value = await api.post('/vulnerabilities/import', { orgId: scanForm.orgId, items })
    await loadAtv()
  } catch (e) { atvErr.value = e.message } finally { refSaving.value = false }
}
const atvRefKind = ref(null)
const arf = reactive({ code: '', name: '', category: '', orgId: 12 })
function openAtvRef(kind) { atvRefKind.value = kind; Object.assign(arf, { code: '', name: '', category: '', orgId: 12 }); atvErr.value = '' }
async function submitAtvRef() {
  refSaving.value = true; atvErr.value = ''
  try {
    const path = atvRefKind.value === 'threat' ? '/threats' : '/vulnerabilities'
    await api.post(path, { orgId: arf.orgId, code: arf.code, name: arf.name, category: arf.category || null, description: null })
    atvRefKind.value = null; await loadAtv()
  } catch (e) { atvErr.value = e.message } finally { refSaving.value = false }
}

// ===== M2 周边：处置决策四选一 + RTP 落地要素（V51）=====
const TD_LABEL = { MITIGATE: '降低', ACCEPT: '接受', TRANSFER: '转移', AVOID: '规避' }
const treatTarget = ref(null)
const tdf = reactive({ decision: 'MITIGATE', plan: '', owner: '', dueDate: '', resource: '', expectedResidual: null })
function openTreat(f) {
  treatTarget.value = f
  Object.assign(tdf, { decision: 'MITIGATE', plan: '', owner: '', dueDate: '', resource: '', expectedResidual: null })
  opError.value = ''
}
async function submitTreat() {
  refSaving.value = true; opError.value = ''
  try {
    await api.post('/risk-findings/' + treatTarget.value.id + '/treatment', { treatmentDecision: tdf.decision, treatmentPlan: tdf.plan })
    // RTP 落地要素（有任一项填了才写，决策与计划互补）
    if (tdf.owner || tdf.dueDate || tdf.resource || tdf.expectedResidual) {
      await api.put('/risk-findings/' + treatTarget.value.id + '/rtp', {
        measure: tdf.plan, owner: tdf.owner || null, dueDate: tdf.dueDate || null,
        resource: tdf.resource || null, expectedResidual: tdf.expectedResidual, status: 'IN_PROGRESS'
      })
    }
    treatTarget.value = null; await loadFindings()
  } catch (e) { opError.value = e.message } finally { refSaving.value = false }
}

onMounted(async () => {
  try {
    liveTasks.value = await api.get('/assessments')
  } catch (e) {
    loadError.value = e.message
  }
  // 并行拉取三个参考库 + 评估计划 + A-T-V + 登记册（七轮 7-5：等级分布真值数据源）
  loadTemplates(); loadControls(); loadKris(); loadPlans(); loadAtv(); loadRegister()
  // A14：任务列表就绪后恢复深链下钻（刷新/收藏直达具体评估）
  await restoreDrillFromRoute()
})

// A14：URL query.a 变化（浏览器前进/后退、外链跳转）驱动下钻开合
watch(() => route.query.a, () => { restoreDrillFromRoute() })

// ---- 合规框架 枚举 → 短标/底色/语义类（统一控件库 + 模板库共用）----
const FW_SHORT = {
  MLPS: '等保', ISO27001: 'ISO', PCI_DSS: 'PCI', PBOC: 'PBOC',
  ISO27701: '27701', ISO20000: '20000', ISO22301: '22301', PIPL: 'PIPL'
}
const FW_BADGE = {
  MLPS: { background: 'var(--info-tint)', color: 'var(--info)' },
  ISO27001: { background: 'var(--accent-weak)', color: 'var(--accent-strong)' },
  PCI_DSS: { background: 'var(--plum-tint)', color: 'var(--plum)' },
  PBOC: { background: 'var(--warning-tint)', color: '#a87d22' },
  ISO27701: { background: 'var(--accent-weak)', color: 'var(--accent-strong)' },
  ISO20000: { background: 'var(--info-tint)', color: 'var(--info)' },
  ISO22301: { background: 'var(--success-tint, rgba(40,150,90,.12))', color: 'var(--success)' },
  PIPL: { background: 'var(--danger-tint)', color: 'var(--danger)' }
}
const FW_PILL = { MLPS: 'blue', ISO27001: 'teal', PCI_DSS: 'violet', PBOC: '', ISO27701: 'teal', ISO20000: 'blue', ISO22301: '', PIPL: 'violet' }
const fwShort = (f) => FW_SHORT[f] || f
const fwBadge = (f) => FW_BADGE[f] || {}
const fwPill = (f) => FW_PILL[f] || ''

// ---- 统一控件库：覆盖体系/复用数（从真实映射派生）----
const ctrlReuse = (id) => (ctrlMappings.value[id] || []).length
// 复用 Top：按映射数排序取前 3，宽度相对最大值归一
const reuseTopLive = computed(() => {
  const rows = controls.value
    .map((c) => ({ name: c.name, code: c.code, v: ctrlReuse(c.id) }))
    .sort((a, b) => b.v - a.v)
    .slice(0, 3)
  const max = rows.length ? Math.max(...rows.map((r) => r.v), 1) : 1
  return rows.map((r) => ({ ...r, w: Math.round((r.v / max) * 100) + '%' }))
})

// ---- KRI 监控：状态统计（KPI 卡）----
const kriStat = computed(() => {
  const s = { total: kris.value.length, critical: 0, warning: 0, normal: 0 }
  for (const k of kris.value) {
    if (k.currentStatus === 'CRITICAL') s.critical++
    else if (k.currentStatus === 'WARNING') s.warning++
    else if (k.currentStatus === 'NORMAL') s.normal++
  }
  return s
})
const KRI_STATUS_CLS = { CRITICAL: 'over', WARNING: 'wait', NORMAL: 'ok', UNKNOWN: 'wait' }
const kriStCls = (s) => KRI_STATUS_CLS[s] || 'wait'
const CTRL_STATUS_CLS = { ACTIVE: 'ok', RETIRED: 'wait' }
const TPL_STATUS_CLS = { DRAFT: 'wait', PUBLISHED: 'ok', RETIRED: 'over' }

// ---- 三个参考库的「新建」弹窗（定义 KRI / 定义控件 / 新建模板）----
const refModal = ref(null) // 'kri' | 'control' | 'template' | null
const refSaving = ref(false)
const refError = ref('')
const kriForm = reactive({ code: '', name: '', unit: '', direction: 'UPPER_BAD', thresholdWarning: null, thresholdCritical: null, owner: '', orgId: 12 })
const ctrlForm = reactive({ code: '', name: '', domain: '', owner: '', orgId: 12 })
const tplForm = reactive({ code: '', name: '', framework: 'MLPS', description: '', owner: '', orgId: 12 })
function openRefModal(kind) {
  refError.value = ''
  if (kind === 'kri') Object.assign(kriForm, { code: '', name: '', unit: '', direction: 'UPPER_BAD', thresholdWarning: null, thresholdCritical: null, owner: '', orgId: 12 })
  if (kind === 'control') Object.assign(ctrlForm, { code: '', name: '', domain: '', owner: '', orgId: 12 })
  if (kind === 'template') Object.assign(tplForm, { code: '', name: '', framework: 'MLPS', description: '', owner: '', orgId: 12 })
  refModal.value = kind
}
async function submitRef() {
  refSaving.value = true; refError.value = ''
  try {
    if (refModal.value === 'kri') {
      await api.post('/kris', { orgId: kriForm.orgId, code: kriForm.code, name: kriForm.name, unit: kriForm.unit, direction: kriForm.direction, thresholdWarning: kriForm.thresholdWarning, thresholdCritical: kriForm.thresholdCritical, owner: kriForm.owner })
      await loadKris()
    } else if (refModal.value === 'control') {
      await api.post('/controls', { orgId: ctrlForm.orgId, code: ctrlForm.code, name: ctrlForm.name, domain: ctrlForm.domain, owner: ctrlForm.owner })
      await loadControls()
    } else {
      await api.post('/assessment-templates', { orgId: tplForm.orgId, code: tplForm.code, name: tplForm.name, framework: tplForm.framework, description: tplForm.description, owner: tplForm.owner })
      await loadTemplates()
    }
    refModal.value = null
  } catch (e) { refError.value = e.message } finally { refSaving.value = false }
}

// ---- 发起评估向导（R1）：① 基本信息 → ② 背景建立 → POST create + PUT context ----
const showCreate = ref(false)
const createStep = ref(1)
const saving = ref(false)
const createError = ref('')
const form = reactive({ title: '', assessor: '', period: '', orgId: 12, templateId: null })
// 背景建立选项（ISO 27005 / GB/T 20984）
const BASIS_OPTS = [
  { v: 'GBT20984', l: 'GB/T 20984' }, { v: 'ISO27001', l: 'ISO/IEC 27001' }, { v: 'ISO27005', l: 'ISO/IEC 27005' },
  { v: 'MLPS', l: '等保 2.0' }, { v: 'PCI_DSS', l: 'PCI DSS' }, { v: 'PIPL', l: '个保法' }, { v: 'PBOC', l: '人行监管' }
]
const METHOD_OPTS = [
  { v: 'INTERVIEW', l: '人员访谈' }, { v: 'DOC_REVIEW', l: '文档核查' }, { v: 'TOOL_SCAN', l: '工具扫描' },
  { v: 'PENTEST', l: '渗透测试' }, { v: 'CONFIG_CHECK', l: '配置核查' }
]
const DEFAULT_CRITERIA = '可能性五级 × 影响五级 → 风险矩阵定级（极低/低/中/高/极高）；残余风险为高/极高须经管理层接受签批方可关闭。'
const ctx = reactive({ scope: '', objective: '', basis: [], methods: [], criteria: DEFAULT_CRITERIA, team: '', startDate: '', endDate: '' })
function openCreate() {
  form.title = ''
  form.assessor = ''
  form.period = ''
  form.orgId = 12
  form.templateId = null
  Object.assign(ctx, { scope: '', objective: '', basis: [], methods: [], criteria: DEFAULT_CRITERIA, team: '', startDate: '', endDate: '' })
  createStep.value = 1
  createError.value = ''
  showCreate.value = true
}
async function submitCreate() {
  saving.value = true
  createError.value = ''
  try {
    const created = await api.post('/assessments', {
      orgId: form.orgId,
      title: form.title,
      assessor: form.assessor || null,
      period: form.period || null,
      templateId: form.templateId || null
    })
    // 背景建立随建随写（有任一项填了才提交，避免空 PUT）
    if (ctx.scope || ctx.objective || ctx.basis.length || ctx.methods.length || ctx.team || ctx.startDate) {
      await api.put('/assessments/' + created.id + '/context', {
        scope: ctx.scope || null, objective: ctx.objective || null,
        basis: ctx.basis.join(',') || null, methods: ctx.methods.join(',') || null,
        criteria: ctx.criteria || null, team: ctx.team || null,
        startDate: ctx.startDate || null, endDate: ctx.endDate || null
      })
    }
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
// ===== UAT 五轮 #1：任务管理（草稿删 / 非草稿作废，CANCELLED 默认隐藏）=====
const showCancelled = ref(false)
const visibleTasks = computed(() => liveTasks.value.filter((r) => showCancelled.value || r.status !== 'CANCELLED'))
async function deleteTask(r) {
  if (!window.confirm(`确认删除草稿评估「${r.title}」？将级联清理其填写、发现与范围资产。`)) return
  try { await api.del('/assessments/' + r.id); liveTasks.value = await api.get('/assessments') }
  catch (e) { loadError.value = e.message }
}
async function cancelTask(r) {
  const reason = window.prompt(`作废评估「${r.title}」（软删留痕，可在"显示已作废"里查看）。作废原因：`, '')
  if (reason === null) return
  try { await api.post('/assessments/' + r.id + '/cancel', { reason: reason || null }); liveTasks.value = await api.get('/assessments') }
  catch (e) { loadError.value = e.message }
}

// ===== 七轮 7-11（B40 P0）：评估生命周期流转（启动/提交复核/通过/驳回）=====
async function flowAction(r, action, label) {
  if (!window.confirm(`确认对「${r.title}」执行「${label}」？`)) return
  try {
    await api.post('/assessments/' + r.id + '/' + action, {})
    liveTasks.value = await api.get('/assessments')
    if (drillId.value === r.id) drillMeta.value = liveTasks.value.find((x) => x.id === r.id) || drillMeta.value
  } catch (e) { window.alert(e.message) }
}
async function rejectTask(r) {
  const reason = window.prompt(`驳回评估「${r.title}」（退回进行中继续修改）。驳回原因：`, '')
  if (reason === null) return
  try {
    await api.post('/assessments/' + r.id + '/reject', { reason: reason || null })
    liveTasks.value = await api.get('/assessments')
  } catch (e) { window.alert(e.message) }
}

// ===== 七轮 7-13（A28）：从系统数据重新预填三张明细表 =====
const reprefillBusy = ref(false)
const formRefreshKey = ref(0)
async function doReprefill() {
  if (!window.confirm('将按当前范围资产与 A-T-V 场景重建 资产清单/威胁脆弱性清单/风险清单 三张系统明细表'
      + '（覆盖这三张表的现有内容，其余手填字段保留）。继续？')) return
  reprefillBusy.value = true
  try {
    await api.post('/assessments/' + drillId.value + '/form/reprefill', {})
    formRefreshKey.value++ // 强制重挂表单组件拉取最新答案
  } catch (e) { window.alert(e.message) } finally { reprefillBusy.value = false }
}

// 评估状态 → 样式类 / i18n 标签键（对齐后端 AssessmentStatus）
const STATUS_CLS = { DRAFT: 'wait', IN_PROGRESS: 'doing', PENDING_REVIEW: 'wait', COMPLETED: 'ok', CANCELLED: 'over' }
const stCls = (s) => STATUS_CLS[s] || 'wait'
const stLabel = (s) => 'risk.assessStatus.' + s

// ---- 风险等级分布（五级）bars（七轮 7-5：由登记册真实发现聚合，残余优先无残余取固有）----
// 极高/极低保留原型内联底色；高/中/低复用 seg2.h/m/l 语义色
const LV_BAR_META = [
  ['VERY_HIGH', 'risk.levelDist.vh', '', '#7a1620'],
  ['HIGH', 'risk.levelDist.h', 'h', null],
  ['MID', 'risk.levelDist.m', 'm', null],
  ['LOW', 'risk.levelDist.l', 'l', null],
  ['VERY_LOW', 'risk.levelDist.vl', '', '#8aa0b3']
]
const levelBars = computed(() => {
  const cnt = {}
  regRows.value.forEach((r) => { const lv = r.residualLevel || r.inherentLevel; if (lv) cnt[lv] = (cnt[lv] || 0) + 1 })
  const max = Math.max(1, ...Object.values(cnt))
  return LV_BAR_META.map(([k, label, cls, bg]) => ({
    label, v: cnt[k] || 0, cls,
    style: { width: Math.round((cnt[k] || 0) * 100 / max) + '%', ...(bg ? { background: bg } : {}) }
  }))
})

// ---- KPI 五卡真值（七轮 7-5）----
const kpiCount = (st) => liveTasks.value.filter((t) => t.status === st).length
const kpiHighRisk = computed(() => liveTasks.value.filter((t) =>
  t.status !== 'CANCELLED' && (t.riskLevel === 'HIGH' || t.riskLevel === 'VERY_HIGH')).length)
const kpiOverdue = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return liveTasks.value.filter((t) =>
    t.endDate && t.endDate < today && t.status !== 'COMPLETED' && t.status !== 'CANCELLED').length
})

// ---- 评估进度漏斗（4 段）----
// width / background 完全照搬原型内联值
// 评估进度漏斗（七轮 7-5：真值——已发起=全部非作废，填写中/待审批/已生效按状态实时计数）
const funnel = computed(() => {
  const all = liveTasks.value.filter((t) => t.status !== 'CANCELLED').length
  const filling = kpiCount('IN_PROGRESS')
  const pending = kpiCount('PENDING_REVIEW')
  const live = kpiCount('COMPLETED')
  const max = Math.max(1, all)
  const w = (v, floor) => Math.max(floor, Math.round(v * 100 / max)) + '%'
  return [
    { key: 'started', v: all, w: w(all, 100), bg: 'var(--accent)' },
    { key: 'filling', v: filling, w: w(filling, 40), bg: 'var(--accent-bright)' },
    { key: 'pending', v: pending, w: w(pending, 30), bg: 'var(--warning)' },
    { key: 'live', v: live, w: w(live, 20), bg: 'var(--success)' }
  ]
})

// ---- 下钻报告 KPI/等级分布（真值：由本评估的风险发现实时计算）----
const drillHigh = computed(() => findings.value.filter((f) => {
  const lv = f.residualLevel || f.inherentLevel
  return lv === 'HIGH' || lv === 'VERY_HIGH'
}).length)
const drillOpen = computed(() => findings.value.filter((f) => f.status === 'OPEN' || f.status === 'IN_TREATMENT').length)
const DRILL_LV = [
  ['VERY_HIGH', '极高', '#7a1620'], ['HIGH', '高', 'var(--danger)'], ['MID', '中', '#a87d22'],
  ['LOW', '低', 'var(--safe, #4a8)'], ['VERY_LOW', '极低', 'var(--accent-bright, var(--accent))']
]
const drillLevelDist = computed(() => {
  const cnt = {}
  findings.value.forEach((f) => { const lv = f.residualLevel || f.inherentLevel || 'UNSET'; cnt[lv] = (cnt[lv] || 0) + 1 })
  const max = Math.max(1, ...Object.values(cnt))
  return DRILL_LV.filter(([k]) => cnt[k]).map(([k, l, c]) => ({ l, v: cnt[k], w: Math.round(cnt[k] * 100 / max) + '%', c }))
})

// ===== 六轮 #2/#5 · 风险登记册（组织级台账，GET /api/risk-findings/register）=====
const regRows = ref([])
const regFltLevel = ref('')
const regFltStatus = ref('')
const LV_TXT = { VERY_HIGH: '极高', HIGH: '高', MID: '中', LOW: '低', VERY_LOW: '极低' }
const lvText = (lv) => LV_TXT[lv] || lv
const REG_ST_TXT = { OPEN: '待处置', IN_TREATMENT: '处置中', DONE: '已处置', VERIFIED: '已验证' }
const REG_ST_CLS = { OPEN: 'wait', IN_TREATMENT: 'doing', DONE: 'ok', VERIFIED: 'ok' }
const regFiltered = computed(() => regRows.value.filter((r) => {
  const lv = r.residualLevel || r.inherentLevel
  if (regFltLevel.value && lv !== regFltLevel.value) return false
  if (regFltStatus.value && r.status !== regFltStatus.value) return false
  return true
}))
const regCount = (st) => regRows.value.filter((r) => r.status === st).length
const regClosed = computed(() => regRows.value.filter((r) => r.status === 'DONE' || r.status === 'VERIFIED').length)
const regHigh = computed(() => regRows.value.filter((r) => {
  const lv = r.residualLevel || r.inherentLevel
  return lv === 'HIGH' || lv === 'VERY_HIGH'
}).length)
// B22 加权风险指数（未闭环按有效等级加权 + 各等级分布，供下钻）
const riskIdx = ref(null)
async function loadRegister() {
  try { regRows.value = await api.get('/risk-findings/register') } catch (e) { regRows.value = [] }
  try { riskIdx.value = await api.get('/risk-findings/index') } catch (e) { riskIdx.value = null }
}
// 点等级芯片 → 用现有等级筛选下钻到该等级的未闭环发现
function drillLevel(level) { regFltLevel.value = regFltLevel.value === level ? '' : level; regFltStatus.value = '' }
// 切到登记册标签即刷新（发现在下钻页流转后回来能看到最新状态）
watch(activeTab, (t) => { if (t === 'register') loadRegister() })
// 点行跳到来源评估的下钻详情
function regGoAssessment(r) {
  const t = liveTasks.value.find((x) => x.id === r.assessmentId)
  if (t) drillInto(t)
}

// ===== M2 深度包 B46：三台账 CSV 导出（登记册/风险场景/KRI，均导当前列表态）=====
function exportRegister() {
  const headers = ['#', '风险发现', '来源评估', '来源', '固有等级', '残余等级', '处置方式', '状态', '更新时间']
  const rows = regFiltered.value.map((r) => [
    r.id, r.title, r.assessmentTitle || '', SRC_TXT[r.source] || r.source || '',
    lvText(r.inherentLevel) || '', lvText(r.residualLevel) || '', TD_LABEL[r.treatmentDecision] || '',
    REG_ST_TXT[r.status] || r.status, r.updatedAt ? String(r.updatedAt).slice(0, 19).replace('T', ' ') : ''
  ])
  exportCsv('风险登记册_' + new Date().toISOString().slice(0, 10) + '.csv', headers, rows)
}
function exportScenarios() {
  const headers = ['#', '资产', '威胁', '脆弱性', '可能性', '影响', '风险值', '固有等级', '说明']
  const rows = scenarios.value.map((s) => [
    s.id, assetNameOf(s.assetId), threatNameOf(s.threatId), vulnNameOf(s.vulnerabilityId),
    s.likelihood, s.impact, s.likelihood * s.impact, lvText(s.inherentLevel) || '', s.description || ''
  ])
  exportCsv('风险场景ATV_' + new Date().toISOString().slice(0, 10) + '.csv', headers, rows)
}
function exportKris() {
  const headers = ['#', '指标编码', '指标名称', '责任人', '当前值', '单位', '预警阈值', '严重阈值', '状态']
  const rows = kris.value.map((k) => [
    k.id, k.code, k.name, k.owner || '', k.currentValue == null ? '' : k.currentValue, k.unit || '',
    k.thresholdWarning, k.thresholdCritical, k.currentStatus
  ])
  exportCsv('KRI监控_' + new Date().toISOString().slice(0, 10) + '.csv', headers, rows)
}

// ===== 八轮 8-11（C11）：日常风险直登 =====
const SRC_TXT = { EVENT: '事件', VULN: '漏洞', AUDIT: '审计', MANUAL: '手工' }
const showDirect = ref(false)
const directBusy = ref(false)
const directErr = ref('')
const df2 = reactive({ title: '', inherentLevel: 'HIGH', source: 'EVENT', orgId: 12 })
function openDirectRisk() {
  Object.assign(df2, { title: '', inherentLevel: 'HIGH', source: 'EVENT', orgId: 12 })
  directErr.value = ''; showDirect.value = true
}
async function submitDirect() {
  directBusy.value = true; directErr.value = ''
  try {
    await api.post('/risk-findings/direct', { orgId: df2.orgId, title: df2.title, inherentLevel: df2.inherentLevel, source: df2.source })
    showDirect.value = false; await loadRegister()
  } catch (e) { directErr.value = e.message } finally { directBusy.value = false }
}

// ===== 八轮 8-10（B41）：KRI 测量录入与趋势 =====
const kriHistoryId = ref(0)
const kriHistory = ref([])
async function recordKri(k) {
  const v = window.prompt(`录入「${k.name}」最新测量值（${k.unit || '数值'}，阈值 预警${k.thresholdWarning}/严重${k.thresholdCritical}）：`, '')
  if (v === null || v.trim() === '' || isNaN(Number(v))) return
  try {
    await api.post('/kris/' + k.id + '/measurements', { value: Number(v), note: null })
    await loadKris()
    if (kriHistoryId.value === k.id) await loadKriHistory(k.id)
  } catch (e) { window.alert(e.message) }
}
async function toggleKriHistory(k) {
  if (kriHistoryId.value === k.id) { kriHistoryId.value = 0; return }
  kriHistoryId.value = k.id
  await loadKriHistory(k.id)
}
async function loadKriHistory(id) {
  try { kriHistory.value = (await api.get('/kris/' + id + '/measurements')).slice(0, 12) } catch (e) { kriHistory.value = [] }
}

// ===== 八轮 8-10（B44）：ATV 三库编辑/删除、场景删除 =====
async function editAtvRef(base, item) {
  const name = window.prompt('名称：', item.name); if (name === null || !name.trim()) return
  const category = window.prompt('分类：', item.category || ''); if (category === null) return
  try {
    await api.put('/' + base + '/' + item.id, { name: name.trim(), category: category || null, description: item.description || null })
    await loadAtv()
  } catch (e) { window.alert(e.message) }
}
async function deleteAtvRef(base, item) {
  if (!window.confirm(`删除「${item.code} ${item.name}」？被风险场景引用的条目会被拒绝。`)) return
  try { await api.del('/' + base + '/' + item.id); await loadAtv() } catch (e) { window.alert(e.message) }
}
async function deleteScenario(s) {
  if (!window.confirm('删除该风险场景？已派生风险发现的场景会被拒绝（保留溯源）。')) return
  try { await api.del('/risk-scenarios/' + s.id); await loadAtv() } catch (e) { window.alert(e.message) }
}

// ===== 六轮 #1 · 模板删除（内置不可删由 v-if 隐藏；被引用后端拒绝并提示走停用）=====
async function deleteTpl(t) {
  if (!window.confirm(`确认删除模板「${t.name}」？被评估任务引用的模板将无法删除（请改用停用）。`)) return
  try {
    await api.del('/assessment-templates/' + t.id)
    templates.value = await api.get('/assessment-templates')
  } catch (e) {
    window.alert(e.message)
  }
}

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

/* ---- 表单引擎 P1：模板卡内 .docx 表单管理 ---- */
.formbox {
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px dashed var(--border-subtle);
  font-size: 11px;
}
.formbox .fb-row { margin-bottom: 6px; }
.formbox .fb-l { color: var(--text-3); }
.formbox .fb-active { color: var(--success); font-weight: 600; }
.formbox .fb-none { color: var(--text-3); }
.formbox .fb-actions { display: flex; gap: 6px; flex-wrap: wrap; }
.formbox .fb-parsed { margin-top: 6px; color: var(--accent-strong); }
.formbox .fb-err { margin-top: 6px; color: var(--danger); }
.formbox .btn.sm { padding: 3px 9px; font-size: 11px; }

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

/* ---- CR-002 关闭门控相关 ---- */
.redline-badge {
  margin-left: 10px;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--danger);
  background: var(--danger-tint, rgba(180, 35, 45, 0.1));
  border: 1px solid var(--danger);
}
.note-strip {
  margin-bottom: 14px;
  padding: 8px 12px;
  font-size: 12.5px;
  color: var(--text-2);
  background: var(--info-tint, rgba(40, 90, 150, 0.08));
  border: 1px solid var(--border);
  border-left: 3px solid var(--info, #3a6ea5);
  border-radius: var(--radius-md);
}
.btn.sm {
  height: 28px;
  padding: 0 12px;
  font-size: 12.5px;
}
td.ops {
  display: flex;
  gap: 8px;
  align-items: center;
}
.muted {
  color: var(--text-3, var(--text-2));
  font-size: 12.5px;
}
.emptyrow {
  text-align: center;
  color: var(--text-2);
  padding: 18px 0;
}
.atv-row { display: flex; align-items: center; gap: 8px; padding: 7px 4px; border-bottom: 1px solid var(--border-subtle); font-size: 12.5px; }

/* ===== R1 · 背景建立向导 + 评估背景卡 ===== */
.modal-card.wide { width: 640px; max-height: 88vh; overflow-y: auto; }
.stepdot { margin-left: 10px; font-size: 11px; font-weight: 600; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 2px 10px; vertical-align: middle; }
.modal-card .fld textarea { display: block; width: 100%; margin-top: 5px; padding: 8px 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13px; font-family: inherit; line-height: 1.6; outline: none; box-sizing: border-box; resize: vertical; }
/* #2 · 依据标准/方式方法：规整两列复选清单（原生 checkbox，accent 主题色） */
.opt-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 6px 10px; margin-top: 7px; }
.opt { display: flex; align-items: center; gap: 8px; height: 32px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); font-size: 12.5px; color: var(--text-2); cursor: pointer; user-select: none; }
/* 特异性需压过 .modal-card .fld input 的整宽规则，否则复选框被拉成整行 */
.opt input, .modal-card .fld .opt input { display: inline-block; width: 14px; height: 14px; margin: 0; padding: 0; accent-color: var(--accent); cursor: pointer; flex-shrink: 0; }
.opt span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.opt.on { border-color: var(--accent); background: var(--accent-weak); color: var(--accent-strong); font-weight: 600; }
.fld-2col { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.ctxgrid { padding-top: 6px; }
.ctxrow { display: flex; gap: 12px; padding: 7px 0; border-bottom: 1px dashed var(--border-subtle); font-size: 12.5px; }
.ctxrow:last-child { border-bottom: 0; }
.ctxrow .ck { flex: 0 0 76px; color: var(--text-3); font-weight: 600; }
.ctxrow .cv { color: var(--text-1); white-space: pre-wrap; line-height: 1.6; }
.ch .sub { font-size: 11px; color: var(--text-3); }
/* R2 · 范围资产 */
.scope-row { display: flex; align-items: center; gap: 9px; padding: 8px 2px; border-bottom: 1px solid var(--border-subtle); font-size: 12.5px; }
.scope-row:last-child { border-bottom: 0; }
.selmini { height: 30px; padding: 0 8px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12px; font-family: inherit; outline: none; max-width: 220px; }
/* 小操作按钮（与通知中心等页同款；danger 悬停变红提示破坏性操作） */
.mini { padding: 3px 9px; font-size: 11px; border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-2); border-radius: 6px; cursor: pointer; margin-right: 4px; }
.mini:hover { background: var(--accent-tint); }
.mini.danger:hover { color: var(--danger); border-color: var(--danger); }
.mini-x { border: 0; background: none; color: var(--text-3); font-size: 11px; cursor: pointer; padding: 2px 6px; border-radius: 4px; }
.mini-x:hover { color: var(--danger); background: var(--danger-tint); }
.mini-a { font-size: 11px; color: var(--accent-strong); text-decoration: none; padding: 2px 8px; border: 1px solid var(--surface-border); border-radius: 6px; }
.mini-a:hover { border-color: var(--accent); }
/* 下钻真值等级分布（复用仪表盘 exbars 视觉） */
.exbars { display: flex; flex-direction: column; gap: 9px; padding: 4px 2px; }
.exbar-row { display: flex; align-items: center; gap: 8px; font-size: 11.5px; }
.exbar-row .exl { color: var(--text-2); }
.exbar-row .extrack { flex: 1; height: 8px; background: var(--bg); border-radius: 5px; overflow: hidden; }
.exbar-row .extrack i { display: block; height: 100%; border-radius: 5px; }
.exbar-row b { width: 26px; text-align: right; font-variant-numeric: tabular-nums; }
/* #3 · 模板完整样式预览 */
.tpl-preview { max-height: 34vh; overflow-y: auto; background: var(--bg); border: 1px solid var(--border-subtle); border-radius: var(--radius-md); padding: 10px 14px; }
.tp-sec { margin-bottom: 10px; }
.tp-sec-title { font-size: 13px; font-weight: 720; color: var(--text-1); font-family: var(--font-display); padding: 6px 0 4px; border-bottom: 1px solid var(--border-subtle); margin-bottom: 6px; }
.tp-field { display: flex; align-items: center; gap: 10px; padding: 3px 0; font-size: 12px; }
.tp-label { color: var(--text-2); min-width: 120px; }
.tp-input { flex: 0 0 auto; padding: 2px 10px; border: 1px dashed var(--surface-border); border-radius: 6px; color: var(--text-3); font-size: 11px; background: var(--surface); }
.tp-input.level, .tp-input.score { border-color: var(--accent); color: var(--accent-strong); }
.tp-list { margin: 6px 0; }
.tp-list-name { font-size: 11.5px; font-weight: 700; color: var(--accent-strong); margin-bottom: 4px; }
.tp-cols { display: flex; flex-wrap: wrap; gap: 6px; }
.tp-col { font-size: 11px; padding: 3px 9px; background: var(--surface); border: 1px solid var(--border-subtle); border-radius: 6px; color: var(--text-2); }
.tp-col i { font-style: normal; color: var(--text-3); margin-left: 4px; font-size: 10px; }
/* R4 · 模板中心 */
.tpl-note { font-size: 12px; color: var(--text-2); background: var(--accent-weak); border-left: 3px solid var(--accent); border-radius: var(--radius-md); padding: 9px 12px; line-height: 1.7; margin-bottom: 6px; }
.tpl-items { max-height: 32vh; overflow-y: auto; }
.tpl-items thead th { text-align: left; font-size: 10.5px; color: var(--text-3); padding: 0 8px 6px; }
.tpl-items tbody td { padding: 6px 8px; border-top: 1px solid var(--border-subtle); vertical-align: top; }
.tpl-forms { max-height: 18vh; overflow-y: auto; }
</style>
