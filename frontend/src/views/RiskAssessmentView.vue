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
        <button class="btn" :disabled="!canWrite('risk.create')" :title="canWrite('risk.create') ? '' : $t('common.noPerm')" @click="openCreate">{{ $t('risk.newAssess') }}</button>
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
          <!-- 表单引擎 P1：选模板则评估按该模板的 .docx 表单填写规范报告（可不选）-->
          <label class="fld">评估模板（选模板则用其报告表单填写，可不选）
            <select v-model="form.templateId">
              <option :value="null">— 不使用模板表单 —</option>
              <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }}（{{ t.code }}）</option>
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
                  @click="drillInto(r)"
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

            <!-- 表单引擎 P1：.docx 报告模板（上传 → 解析 → 启用）-->
            <div class="formbox">
              <div class="fb-row">
                <span class="fb-l">报告表单：</span>
                <span v-if="tplFormState(t.id).active" class="fb-active">
                  ✓ 已启用 {{ tplFormState(t.id).active.name }}（v{{ tplFormState(t.id).active.versionNo }}）
                </span>
                <span v-else class="fb-none">未配置</span>
              </div>
              <input
                type="file" accept=".docx" style="display:none"
                :id="'docx-' + t.id"
                @change="onDocxPicked(t.id, $event)"
              />
              <div class="fb-actions">
                <button class="btn ghost sm" :disabled="!canWriteRisk || tplFormState(t.id).busy" @click="pickDocx(t.id)">
                  {{ tplFormState(t.id).busy ? '处理中…' : '上传 .docx 模板' }}
                </button>
                <!-- 上传出的最新草稿可一键启用 -->
                <button
                  v-if="tplFormState(t.id).parsed && (!tplFormState(t.id).active || tplFormState(t.id).active.id !== tplFormState(t.id).parsed.id)"
                  class="btn sm" :disabled="!canWriteRisk || tplFormState(t.id).busy"
                  @click="activateTplForm(t.id, tplFormState(t.id).parsed.id)"
                >启用 v{{ tplFormState(t.id).parsed.versionNo }}</button>
              </div>
              <div v-if="tplFormState(t.id).parsed" class="fb-parsed">
                解析：{{ tplFormState(t.id).parsed.sections }} 章节 · {{ tplFormState(t.id).parsed.fields }} 字段 · {{ tplFormState(t.id).parsed.lists }} 明细表
              </div>
              <div v-if="tplFormState(t.id).error" class="fb-err">{{ tplFormState(t.id).error }}</div>
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
              </tr>
            </thead>
            <tbody>
              <tr v-for="k in kris" :key="k.id">
                <td>{{ k.name }} <span class="code">{{ k.code }}</span></td>
                <td><span class="pill">{{ k.owner || '—' }}</span></td>
                <td class="num">{{ k.currentValue == null ? '—' : k.currentValue }}<span v-if="k.unit" class="muted"> {{ k.unit }}</span></td>
                <td class="num">{{ k.thresholdWarning }} / {{ k.thresholdCritical }}</td>
                <td>
                  <span class="st" :class="kriStCls(k.currentStatus)"><span class="d"></span>{{ $t('risk.kri.cstatus.' + k.currentStatus) }}</span>
                </td>
              </tr>
              <tr v-if="!kris.length">
                <td colspan="5" class="emptyrow">{{ $t('risk.kri.empty') }}</td>
              </tr>
            </tbody>
          </table>
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
          <label class="fld">{{ $t('risk.ref.org') }}<select v-model.number="kriForm.orgId"><option :value="12">{{ $t('risk.create.orgPay') }}</option><option :value="13">{{ $t('risk.create.orgConsumer') }}</option></select></label>
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
          <label class="fld">{{ $t('risk.ref.org') }}<select v-model.number="ctrlForm.orgId"><option :value="12">{{ $t('risk.create.orgPay') }}</option><option :value="13">{{ $t('risk.create.orgConsumer') }}</option></select></label>
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
            <select v-model="tplForm.framework"><option value="MLPS">{{ $t('risk.templates.fw.MLPS') }}</option><option value="ISO27001">{{ $t('risk.templates.fw.ISO27001') }}</option><option value="PCI_DSS">{{ $t('risk.templates.fw.PCI_DSS') }}</option><option value="PBOC">{{ $t('risk.templates.fw.PBOC') }}</option></select>
          </label>
          <label class="fld">{{ $t('risk.templates.f.desc') }}<input v-model="tplForm.description" /></label>
          <label class="fld">{{ $t('risk.ref.owner') }}<input v-model="tplForm.owner" /></label>
          <label class="fld">{{ $t('risk.ref.org') }}<select v-model.number="tplForm.orgId"><option :value="12">{{ $t('risk.create.orgPay') }}</option><option :value="13">{{ $t('risk.create.orgConsumer') }}</option></select></label>
          <p v-if="refError" class="cerr">{{ refError }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="refModal = null">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!tplForm.code || !tplForm.name || refSaving" @click="submitRef">{{ refSaving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 下钻视图：评估报告（固有/残余风险 + 管理层接受） ============ -->
    <section v-show="drill" class="view view-risk">
      <div class="phead">
        <div>
          <span class="bk" @click="drill = false">{{ $t('risk.report.back') }}</span>
          <div class="kqt">RA-{{ drillId }} · {{ $t('risk.tag') }}</div>
          <h1>{{ drillTitle || $t('risk.report.title') }}</h1>
        </div>
        <div class="sp"></div>
        <span v-if="exportError" class="cerr" style="margin-right: 10px">{{ exportError }}</span>
        <!-- 表单引擎 P3：回填上传模板 → 导出 Word/PDF（格式同官方模板，可交审计） -->
        <button class="btn ghost" :disabled="!!exporting" @click="exportReport('docx')">
          {{ exporting === 'docx' ? '导出中…' : '导出 Word' }}
        </button>
        <button class="btn" :disabled="!!exporting" @click="exportReport('pdf')">
          {{ exporting === 'pdf' ? '导出中…' : '导出 PDF' }}
        </button>
      </div>

      <!-- 表单引擎 P1：按模板 .docx 解析出的规范评估表单（真实后端，可填写保存） -->
      <AssessmentFormFill :assessment-id="drillId" @saved="onFormSaved" />

      <!-- 表单引擎 P2：整体残余等级 + 管理层接受签批（CR-002 完成门控） -->
      <AssessmentSignoff :assessment-id="drillId" ref="signoffRef" />

      <!-- 诚实标注：下方分析图示为原型视觉示意；「评估表单」与「风险发现·关闭门控」为真实后端数据与红线 -->
      <div class="note-strip">{{ $t('risk.gate.scaffoldNote') }}</div>

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
import { ref, reactive, computed, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import AssessmentFormFill from '@/components/AssessmentFormFill.vue'
import AssessmentSignoff from '@/components/AssessmentSignoff.vue'
import { api } from '@/api/client.js'
import { canWrite } from '@/auth.js'

// ---- Tab 切换 ----
const tabs = ['tasks', 'templates', 'controls', 'kri']
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

async function drillInto(r) {
  drill.value = true
  drillId.value = r.id
  drillTitle.value = r.title
  await loadFindings()
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
  } catch (e) { controls.value = []; ctrlMappings.value = {} }
}
async function loadKris() {
  try { kris.value = await api.get('/kris') } catch (e) { kris.value = [] }
}

onMounted(async () => {
  try {
    liveTasks.value = await api.get('/assessments')
  } catch (e) {
    loadError.value = e.message
  }
  // 并行拉取三个参考库
  loadTemplates(); loadControls(); loadKris()
})

// ---- 合规框架 枚举 → 短标/底色/语义类（统一控件库 + 模板库共用）----
const FW_SHORT = { MLPS: '等保', ISO27001: 'ISO', PCI_DSS: 'PCI', PBOC: 'PBOC' }
const FW_BADGE = {
  MLPS: { background: 'var(--info-tint)', color: 'var(--info)' },
  ISO27001: { background: 'var(--accent-weak)', color: 'var(--accent-strong)' },
  PCI_DSS: { background: 'var(--plum-tint)', color: 'var(--plum)' },
  PBOC: { background: 'var(--warning-tint)', color: '#a87d22' }
}
const FW_PILL = { MLPS: 'blue', ISO27001: 'teal', PCI_DSS: 'violet', PBOC: '' }
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

// ---- 发起评估：登记弹窗 → POST /api/assessments → 刷新列表 ----
const showCreate = ref(false)
const saving = ref(false)
const createError = ref('')
const form = reactive({ title: '', assessor: '', period: '', orgId: 12, templateId: null })
function openCreate() {
  form.title = ''
  form.assessor = ''
  form.period = ''
  form.orgId = 12
  form.templateId = null
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
      period: form.period || null,
      templateId: form.templateId || null
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

// ---- 下钻报告：风险点清单 ----
const pointRows = [
  { key: 'acl', lvl: 'h', cls: 'h' },
  { key: 'tls', lvl: 'h', cls: 'h' },
  { key: 'log', lvl: 'l', cls: 'l' }
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
</style>
