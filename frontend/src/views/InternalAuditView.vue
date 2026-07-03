<template>
  <!--
    内部审计（InternalAuditView · M3）：审计计划 → 审计发现 → 整改跟踪。
    功能真源 = 后端 audit/management（/api/audit-plans?type=INTERNAL、/api/audit-findings、/api/remediation-orders）。
    与外部审计共用审计管理后端，按 auditType=INTERNAL 分视图；写门控 "extaudit"（后端审计管理写权限）。
    检查表执行（表单引擎接入）后置——见底部说明。
  -->
  <AppShell>
    <section class="view view-ia">
      <div class="phead">
        <div><div class="kqt">M3 · 内部审计</div><h1>内部审计实施与管理</h1></div>
        <div class="sp"></div>
        <button v-if="tab === 'plan'" class="btn" :disabled="!canWrite('extaudit')"
                :title="canWrite('extaudit') ? '' : '无审计管理写权限'" @click="openPlan">＋ 新建审计计划</button>
      </div>

      <!-- KPI -->
      <div class="kpibar">
        <div class="kc"><div class="l">内审计划</div><div class="v">{{ plans.length }}</div></div>
        <div class="kc"><div class="l">实施中</div><div class="v" style="color:var(--accent-strong)">{{ inProgress }}</div></div>
        <div class="kc"><div class="l">审计发现</div><div class="v">{{ findings.length }}</div></div>
        <div class="kc"><div class="l">高风险发现</div><div class="v" style="color:var(--danger)">{{ highFindings }}</div></div>
      </div>

      <div class="tabbar">
        <button :class="{ on: tab === 'annual' }" @click="tab = 'annual'; loadAnnual()">年度计划</button>
        <button :class="{ on: tab === 'plan' }" @click="tab = 'plan'">审计计划</button>
        <button :class="{ on: tab === 'finding' }" @click="tab = 'finding'">审计发现</button>
        <button :class="{ on: tab === 'remed' }" @click="tab = 'remed'">整改跟踪</button>
        <button :class="{ on: tab === 'proc' }" @click="tab = 'proc'; loadProcedures()">程序底稿</button>
        <button :class="{ on: tab === 'evidence' }" @click="tab = 'evidence'; loadEvidence()">证据库</button>
        <button :class="{ on: tab === 'report' }" @click="tab = 'report'; loadReport()">审计报告</button>
      </div>

      <!-- 审计计划 -->
      <div v-show="tab === 'plan'" class="card">
        <div class="ch"><h3>审计计划</h3><span class="cnt">{{ plans.length }}</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:720px">
            <thead><tr><th>编号</th><th>主题</th><th>类型</th><th>开始日</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="p in plans" :key="p.id" class="clk" :class="{ on: p.id === planId }" @click="pickPlan(p)">
                <td class="code">AP-{{ p.id }}</td>
                <td><b>{{ p.title }}</b></td>
                <td><span class="pill">内部审计</span></td>
                <td class="num">{{ p.planStartDate || '—' }}</td>
                <td><span class="st" :class="PLAN_CLS[p.status]"><span class="d"></span>{{ PLAN_LABEL[p.status] }}</span></td>
                <td class="ops" @click.stop>
                  <template v-if="canWrite('extaudit')">
                    <button v-if="p.status==='PLANNED'" class="mini" @click="planAction(p,'start')">开始</button>
                    <button v-if="p.status==='IN_PROGRESS'" class="mini" @click="planAction(p,'report')">出具报告</button>
                    <button v-if="p.status==='REPORTING'" class="mini" @click="planAction(p,'close')">关闭</button>
                    <button v-if="p.status==='PLANNED'||p.status==='IN_PROGRESS'" class="mini danger" @click="planAction(p,'cancel')">取消</button>
                    <button v-if="!p.checklistTemplateId && !p.checklistAssessmentId" class="mini" @click="openBind(p)">绑定检查表</button>
                    <button v-else-if="!p.checklistAssessmentId" class="mini" @click="startChecklist(p)">执行检查表</button>
                    <button v-else class="mini" @click="gotoChecklist(p)">检查表 #{{ p.checklistAssessmentId }}</button>
                  </template>
                  <button class="mini" @click="openNotice(p)">通知书{{ p.noticeIssuedAt ? ' ✓' : '' }}</button>
                  <button v-if="p.status==='CLOSED' && canWrite('extaudit')" class="mini" @click="followUp(p)">后续审计</button>
                  <span v-if="p.followUpOf" class="pill" title="后续审计：验证原计划整改有效性">↩ AP-{{ p.followUpOf }}</span>
                  <button class="mini" @click="exportDossier(p)">卷宗导出</button>
                </td>
              </tr>
              <tr v-if="!plans.length"><td colspan="6" class="emptyrow">暂无内审计划，点「＋ 新建审计计划」。</td></tr>
            </tbody>
          </table>
          <p v-if="opMsg" class="ok-msg">{{ opMsg }}</p>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
        </div>
      </div>

      <!-- 审计发现 -->
      <div v-show="tab === 'finding'" class="card">
        <div class="ch">
          <h3>审计发现</h3>
          <select class="sel" v-model.number="planId" @change="loadFindings">
            <option :value="0" disabled>— 选择审计计划 —</option>
            <option v-for="p in plans" :key="p.id" :value="p.id">AP-{{ p.id }} · {{ p.title }}</option>
          </select>
          <button v-if="planId && canWrite('extaudit')" class="btn sm" style="margin-left:auto" @click="openFinding">＋ 新建发现</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <div v-if="!planId" class="hint">先选择一个审计计划。</div>
          <table v-else style="min-width:600px">
            <thead><tr><th>编号</th><th>问题</th><th>严重度</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="f in findings" :key="f.id" class="clk" :class="{ on: f.id === findingId }" @click="pickFinding(f)">
                <td class="code">AF-{{ f.id }}</td>
                <td><b>{{ f.title }}</b></td>
                <td><span class="tag" :class="SEV_CLS[f.severity]">{{ SEV_LABEL[f.severity] }}</span></td>
                <td class="ops" @click.stop>
                  <button class="mini" @click="openDetail(f)">五要素{{ f.conditionDesc ? ' ✓' : '' }}</button>
                  <button v-if="canWrite('extaudit')" class="mini" @click="openRemed(f)">下达整改</button>
                </td>
              </tr>
              <tr v-if="!findings.length"><td colspan="4" class="emptyrow">该计划暂无审计发现。</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 整改跟踪 -->
      <div v-show="tab === 'remed'" class="card">
        <div class="ch">
          <h3>整改跟踪</h3>
          <select class="sel" v-model.number="findingId" @change="loadRemed">
            <option :value="0" disabled>— 选择审计发现 —</option>
            <option v-for="f in findings" :key="f.id" :value="f.id">AF-{{ f.id }} · {{ f.title }}</option>
          </select>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <div v-if="!findingId" class="hint">先在「审计发现」选一条发现，或在此选择。</div>
          <table v-else style="min-width:640px">
            <thead><tr><th>编号</th><th>责任人</th><th>整改措施</th><th>截止</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="r in remeds" :key="r.id">
                <td class="code">RO-{{ r.id }}</td>
                <td>{{ r.assignee || '—' }}</td>
                <td class="muted">{{ r.measure || '—' }}</td>
                <td class="num">{{ r.dueDate || '—' }}</td>
                <td><span class="st" :class="REM_CLS[r.status]"><span class="d"></span>{{ REM_LABEL[r.status] }}</span></td>
                <td class="ops">
                  <template v-if="canWrite('extaudit')">
                    <button v-if="r.status==='PENDING'" class="mini" @click="remAction(r,'start')">开始</button>
                    <button v-if="r.status==='IN_PROGRESS'" class="mini" @click="remAction(r,'submit')">提交</button>
                    <button v-if="r.status==='SUBMITTED'" class="mini" @click="remAction(r,'verify')">验证通过</button>
                    <button v-if="r.status==='SUBMITTED'" class="mini danger" @click="remAction(r,'reject')">驳回</button>
                  </template>
                </td>
              </tr>
              <tr v-if="!remeds.length"><td colspan="6" class="emptyrow">该发现暂无整改单，去「审计发现」下达整改。</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 年度计划（V52 · A3：风险导向排项目 → 批准 → 逐项立项）-->
      <div v-show="tab === 'annual'" class="card">
        <div class="ch">
          <h3>年度审计计划</h3>
          <select class="sel" v-model.number="annualId" @change="loadAnnualItems">
            <option :value="0" disabled>— 选择年度计划 —</option>
            <option v-for="a in annuals" :key="a.id" :value="a.id">{{ a.year }} · {{ a.title }}（{{ a.status === 'APPROVED' ? '已批准' : '草稿' }}）</option>
          </select>
          <div style="margin-left:auto;display:flex;gap:8px" v-if="canWrite('extaudit')">
            <button class="btn ghost sm" @click="showAnnualNew = true">＋ 新建年度计划</button>
            <template v-if="annualId && currentAnnual">
              <button v-if="currentAnnual.status==='DRAFT'" class="btn ghost sm" @click="showAnnualItem = true">＋ 纳入对象</button>
              <button v-if="currentAnnual.status==='DRAFT'" class="btn sm" @click="approveAnnual">批准（冻结清单）</button>
            </template>
          </div>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <div v-if="!annualId" class="hint">年度层：按风险排序纳入审计对象 → 批准冻结 → 逐项转单项审计计划（防重复立项）。</div>
          <table v-else style="min-width:760px">
            <thead><tr><th>风险序</th><th>审计对象</th><th>排期</th><th>关注要点</th><th>立项</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="it in annualItems" :key="it.id">
                <td><span class="tag" :class="it.riskRank <= 2 ? 'h' : (it.riskRank === 3 ? 'm' : '')">#{{ it.riskRank }}</span></td>
                <td><b>{{ it.target }}</b></td>
                <td class="num">{{ it.quarter }}</td>
                <td class="muted">{{ it.note || '—' }}</td>
                <td><span v-if="it.planId" class="code">AP-{{ it.planId }}</span><span v-else class="muted">未立项</span></td>
                <td class="ops">
                  <button v-if="!it.planId && currentAnnual && currentAnnual.status==='APPROVED' && canWrite('extaudit')"
                          class="mini" @click="itemToPlan(it)">转审计计划</button>
                </td>
              </tr>
              <tr v-if="!annualItems.length"><td colspan="6" class="emptyrow">暂无审计对象，点「＋ 纳入对象」。</td></tr>
            </tbody>
          </table>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
        </div>
      </div>

      <!-- 新建年度计划弹窗 -->
      <div v-if="showAnnualNew" class="modal-mask" @click.self="showAnnualNew = false">
        <div class="modal-card">
          <h3>新建年度审计计划</h3>
          <label class="fld">计划年度<input type="number" v-model.number="af2.year" /></label>
          <label class="fld">标题（可空，默认「{{ af2.year }} 年度内部审计计划」）<input v-model="af2.title" /></label>
          <label class="fld">所属组织<select v-model.number="af2.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAnnualNew = false">取消</button>
            <button class="btn" :disabled="!af2.year || saving" @click="submitAnnual">{{ saving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>

      <!-- 纳入审计对象弹窗 -->
      <div v-if="showAnnualItem" class="modal-mask" @click.self="showAnnualItem = false">
        <div class="modal-card">
          <h3>纳入审计对象</h3>
          <label class="fld">审计对象（单位/系统/流程）<input v-model="aif.target" placeholder="如 支付结算系统" /></label>
          <div class="fld-2col" style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
            <label class="fld">风险排序（1 最高）
              <select v-model.number="aif.riskRank"><option :value="1">1</option><option :value="2">2</option><option :value="3">3</option><option :value="4">4</option><option :value="5">5</option></select>
            </label>
            <label class="fld">排期
              <select v-model="aif.quarter"><option>Q1</option><option>Q2</option><option>Q3</option><option>Q4</option></select>
            </label>
          </div>
          <label class="fld">关注要点/理由<input v-model="aif.note" placeholder="如 备付金合规重点、上年发现较多" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAnnualItem = false">取消</button>
            <button class="btn" :disabled="!aif.target || saving" @click="submitAnnualItem">{{ saving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>

      <!-- 程序底稿（V50 · A2：审计程序 → 执行留底稿 → 复核）-->
      <div v-show="tab === 'proc'" class="card">
        <div class="ch">
          <h3>审计程序 / 工作底稿</h3>
          <select class="sel" v-model.number="procPlanId" @change="loadProcedures">
            <option :value="0" disabled>— 选择审计计划 —</option>
            <option v-for="p in plans" :key="p.id" :value="p.id">AP-{{ p.id }} · {{ p.title }}</option>
          </select>
          <span class="sub">执行记录即底稿 · 复核人须≠执行人</span>
          <button v-if="procPlanId && canWrite('extaudit')" class="btn sm" style="margin-left:auto" @click="showProcAdd = true">＋ 新增程序</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <div v-if="!procPlanId" class="hint">选择计划后维护审计程序：程序（做什么/验证什么）→ 执行留工作底稿（WP 编号）→ 交叉复核。</div>
          <table v-else style="min-width:860px">
            <thead><tr><th>底稿号</th><th>程序步骤</th><th>目标</th><th>执行记录（底稿）</th><th>执行</th><th>复核</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="pr in procedures" :key="pr.id">
                <td class="code">{{ pr.workpaperNo }}</td>
                <td><b>{{ pr.name }}</b></td>
                <td class="muted">{{ pr.objective || '—' }}</td>
                <td class="muted" style="max-width:260px">{{ pr.result || '—' }}</td>
                <td class="muted">{{ pr.executor || '—' }}</td>
                <td class="muted">{{ pr.reviewer || '—' }}</td>
                <td><span class="st" :class="PROC_CLS[pr.status]"><span class="d"></span>{{ PROC_LABEL[pr.status] }}</span></td>
                <td class="ops">
                  <template v-if="canWrite('extaudit')">
                    <button v-if="pr.status==='PENDING'" class="mini" @click="openProcExec(pr)">执行</button>
                    <button v-if="pr.status==='DONE'" class="mini" @click="reviewProc(pr)">复核</button>
                  </template>
                </td>
              </tr>
              <tr v-if="!procedures.length"><td colspan="8" class="emptyrow">暂无程序，点「＋ 新增程序」建立审计程序表。</td></tr>
            </tbody>
          </table>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
        </div>
      </div>

      <!-- 通知书弹窗（V50：签发后冻结）-->
      <div v-if="noticeTarget" class="modal-mask" @click.self="noticeTarget = null">
        <div class="modal-card">
          <h3>审计通知书 · AP-{{ noticeTarget.id }}</h3>
          <p v-if="noticeTarget.noticeIssuedAt" class="rpt-issued" style="margin-bottom:12px">✓ 已签发 · {{ noticeTarget.noticeIssuedBy }} · {{ fmtDt(noticeTarget.noticeIssuedAt) }}（内容已冻结）</p>
          <label class="fld">被审计单位/部门<input v-model="nf.auditee" :disabled="!!noticeTarget.noticeIssuedAt" /></label>
          <label class="fld">审计范围<textarea v-model="nf.noticeScope" rows="2" :disabled="!!noticeTarget.noticeIssuedAt"></textarea></label>
          <label class="fld">审计依据<input v-model="nf.noticeBasis" :disabled="!!noticeTarget.noticeIssuedAt" placeholder="如 2026 年度内审计划第 3 项" /></label>
          <label class="fld">审计组成员（组长在前）<input v-model="nf.auditTeam" :disabled="!!noticeTarget.noticeIssuedAt" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <a v-if="noticeTarget.noticeIssuedAt" class="btn ghost" :href="'/api/audit-plans/' + noticeTarget.id + '/notice.docx'" target="_blank" style="text-decoration:none">导出通知书 .docx</a>
            <button class="btn ghost" @click="noticeTarget = null">关闭</button>
            <template v-if="!noticeTarget.noticeIssuedAt && canWrite('extaudit')">
              <button class="btn ghost" :disabled="saving" @click="saveNotice(false)">保存草稿</button>
              <button class="btn" :disabled="!nf.auditee || saving" @click="saveNotice(true)">{{ saving ? '提交中…' : '签发通知书' }}</button>
            </template>
          </div>
        </div>
      </div>

      <!-- 新增程序弹窗 -->
      <div v-if="showProcAdd" class="modal-mask" @click.self="showProcAdd = false">
        <div class="modal-card">
          <h3>新增审计程序</h3>
          <label class="fld">程序步骤（做什么）<input v-model="pf2.name" placeholder="如 抽样核验离职账号禁用情况" /></label>
          <label class="fld">程序目标（验证什么）<input v-model="pf2.objective" placeholder="如 验证账号回收控制有效性" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showProcAdd = false">取消</button>
            <button class="btn" :disabled="!pf2.name || saving" @click="submitProc">{{ saving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>

      <!-- 执行程序（落底稿）弹窗 -->
      <div v-if="procExecTarget" class="modal-mask" @click.self="procExecTarget = null">
        <div class="modal-card wide2">
          <h3>执行程序 · {{ procExecTarget.workpaperNo }}</h3>
          <p class="muted" style="margin:-6px 0 12px">{{ procExecTarget.name }}<br/>执行记录即工作底稿，提交后不可覆盖（如需补充另立程序）。</p>
          <label class="fld">执行记录（工作底稿）<textarea v-model="procResult" rows="5" placeholder="做了什么、抽了哪些样本、发现了什么、证据在哪…"></textarea></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="procExecTarget = null">取消</button>
            <button class="btn" :disabled="!procResult.trim() || saving" @click="submitProcExec">{{ saving ? '提交中…' : '提交底稿' }}</button>
          </div>
        </div>
      </div>

      <!-- 证据库（V44：上传/反向取证/关联回溯）-->
      <div v-show="tab === 'evidence'" class="card">
        <div class="ch">
          <h3>证据库</h3><span class="cnt">{{ evidences.length }}</span>
          <span class="sub">SHA-256 指纹固化 · 反向取证可校验篡改</span>
          <button v-if="canWrite('extaudit')" class="btn sm" style="margin-left:auto" @click="openEvUpload">＋ 上传证据</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:860px">
            <thead><tr><th>编号</th><th>名称</th><th>文件</th><th>关联对象</th><th>指纹（SHA-256 前 12 位）</th><th>上传</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="e in evidences" :key="e.id">
                <td class="code">EV-{{ e.id }}</td>
                <td><b>{{ e.name }}</b></td>
                <td class="muted">{{ e.fileName || '—' }} · {{ (e.sizeBytes / 1024).toFixed(1) }}KB</td>
                <td class="muted">
                  <span v-if="e.planId">AP-{{ e.planId }} </span>
                  <span v-if="e.findingId">AF-{{ e.findingId }} </span>
                  <span v-if="e.remediationId">RO-{{ e.remediationId }}</span>
                </td>
                <td class="code" :title="e.sha256">{{ (e.sha256 || '').slice(0, 12) }}…</td>
                <td class="muted">{{ e.uploadedBy || '—' }}</td>
                <td class="ops">
                  <a class="mini" :href="'/api/evidence/' + e.id + '/download'" target="_blank">下载</a>
                  <button class="mini" @click="verifyEv(e)">反向取证</button>
                </td>
              </tr>
              <tr v-if="!evidences.length"><td colspan="7" class="emptyrow">暂无证据，点「＋ 上传证据」并关联 计划/发现/整改单。</td></tr>
            </tbody>
          </table>
          <div v-if="verifyResult" class="verify-box" :class="verifyResult.intact ? 'ok' : 'bad'">
            <b>{{ verifyResult.intact ? '✓ 完整性校验通过' : '✕ 指纹不一致——文件可能被篡改！' }}</b>
            <span>EV-{{ verifyResult.evidenceId }} · 固化指纹 {{ verifyResult.storedSha256.slice(0, 16) }}… · 现算 {{ verifyResult.actualSha256.slice(0, 16) }}…</span>
            <span v-if="verifyResult.planTitle">回溯：计划「{{ verifyResult.planTitle }}」</span>
            <span v-if="verifyResult.findingTitle">发现「{{ verifyResult.findingTitle }}」</span>
          </div>
        </div>
      </div>

      <!-- 审计报告（V47 · A1：自动组稿 → 征求意见 → 定稿(选意见) → 签发）-->
      <div v-show="tab === 'report'" class="card">
        <div class="ch">
          <h3>审计报告</h3>
          <select class="sel" v-model.number="reportPlanId" @change="loadReport">
            <option :value="0" disabled>— 选择审计计划 —</option>
            <option v-for="p in plans" :key="p.id" :value="p.id">AP-{{ p.id }} · {{ p.title }}</option>
          </select>
          <template v-if="reportPlanId">
            <span v-if="report" class="st" :class="RPT_CLS[report.status]" style="margin-left:8px"><span class="d"></span>{{ RPT_LABEL[report.status] }}</span>
            <div style="margin-left:auto;display:flex;gap:8px" v-if="canWrite('extaudit')">
              <button v-if="!report" class="btn sm" @click="createReport">生成报告草稿（自动组稿）</button>
              <template v-else>
                <button v-if="report.status==='DRAFT'||report.status==='COMMENTING'" class="btn ghost sm" :disabled="rptSaving" @click="saveReport">{{ rptSaving ? '保存中…' : '保存' }}</button>
                <button v-if="report.status==='DRAFT'" class="btn sm" @click="rptAction('comment')">征求意见</button>
                <button v-if="report.status==='COMMENTING'" class="btn sm" @click="rptAction('finalize')">定稿</button>
                <button v-if="report.status==='FINAL'" class="btn sm" @click="rptAction('issue')">签发</button>
              </template>
            </div>
          </template>
        </div>
        <div class="cb">
          <div v-if="!reportPlanId" class="hint">选择一个审计计划：无报告可一键自动组稿（计划+发现五要素+整改台账），再走 征求意见 → 定稿（选审计意见）→ 签发。</div>
          <template v-else-if="report">
            <div class="rpt-meta">
              <label class="fld">报告标题<input v-model="rptEdit.title" :disabled="rptFrozen" /></label>
              <label class="fld">审计意见（定稿必选）
                <select v-model="rptEdit.opinion" :disabled="rptFrozen">
                  <option :value="null">— 未定 —</option>
                  <option value="SATISFACTORY">满意</option>
                  <option value="GENERALLY_SATISFACTORY">基本满意</option>
                  <option value="NEEDS_IMPROVEMENT">需改进</option>
                  <option value="UNSATISFACTORY">不满意</option>
                </select>
              </label>
            </div>
            <label class="fld">审计概述与总体评价
              <textarea v-model="rptEdit.summary" rows="2" :disabled="rptFrozen" class="rpt-ta"></textarea>
            </label>
            <label class="fld">报告正文（自动组稿，可编辑）
              <textarea v-model="rptEdit.content" rows="14" :disabled="rptFrozen" class="rpt-ta mono"></textarea>
            </label>
            <div style="display:flex;gap:10px;align-items:center;margin-top:4px">
              <a class="btn ghost sm" :href="'/api/audit-reports/' + report.id + '/docx'" target="_blank" style="text-decoration:none">导出报告 .docx</a>
              <div v-if="report.status==='ISSUED'" class="rpt-issued" style="flex:1">✓ 已签发 · {{ report.issuedBy }} · {{ fmtDt(report.issuedAt) }}（正文已冻结）</div>
            </div>
            <p v-if="opErr" class="cerr">{{ opErr }}</p>
          </template>
          <div v-else class="hint">该计划暂无报告——点右上「生成报告草稿」。</div>
        </div>
      </div>

      <p class="note">检查表执行复用风险评估同一套 .docx 表单引擎：在计划上「绑定检查表」（选评估模板）→「执行检查表」生成评估 → 到风险评估页填写/导出。</p>

      <!-- 发现五要素弹窗（V47 · IIA 4C+R：现状/标准/原因/影响/建议 + 管理层回应）-->
      <div v-if="detailTarget" class="modal-mask" @click.self="detailTarget = null">
        <div class="modal-card wide2">
          <h3>发现五要素 · AF-{{ detailTarget.id }}<span class="muted" style="font-weight:400;font-size:12.5px;margin-left:8px">{{ detailTarget.title }}</span></h3>
          <label class="fld">现状（condition：客观事实）<textarea v-model="df.conditionDesc" rows="2"></textarea></label>
          <label class="fld">标准（criteria：应遵循的制度/法规）<textarea v-model="df.criteriaDesc" rows="2"></textarea></label>
          <label class="fld">原因（cause）<textarea v-model="df.cause" rows="2"></textarea></label>
          <label class="fld">影响（effect：风险与后果）<textarea v-model="df.effect" rows="2"></textarea></label>
          <label class="fld">建议（recommendation）<textarea v-model="df.recommendation" rows="2"></textarea></label>
          <div class="resp-box">
            <div class="resp-h">管理层回应</div>
            <div v-if="detailTarget.mgmtResponse" class="resp-v">{{ detailTarget.mgmtResponse }}<span class="muted">　— {{ detailTarget.responseBy }} · {{ fmtDt(detailTarget.responseAt) }}</span></div>
            <div v-else class="resp-add">
              <input v-model="df.response" placeholder="被审计单位意见 / 整改承诺…" />
              <button class="btn ghost sm" :disabled="!df.response || saving" @click="submitResponse">提交回应</button>
            </div>
          </div>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="detailTarget = null">关闭</button>
            <button v-if="canWrite('extaudit')" class="btn" :disabled="saving" @click="submitDetail">{{ saving ? '保存中…' : '保存五要素' }}</button>
          </div>
        </div>
      </div>

      <!-- 上传证据弹窗 -->
      <div v-if="showEvUpload" class="modal-mask" @click.self="showEvUpload = false">
        <div class="modal-card">
          <h3>上传证据</h3>
          <label class="fld">证据名称/说明<input v-model="ev.name" placeholder="如 防火墙策略截图" /></label>
          <label class="fld">文件<input type="file" @change="onEvFile" /></label>
          <label class="fld">关联审计计划<select v-model.number="ev.planId"><option :value="0">— 不关联 —</option><option v-for="p in plans" :key="p.id" :value="p.id">AP-{{ p.id }} · {{ p.title }}</option></select></label>
          <label class="fld">关联审计发现<select v-model.number="ev.findingId"><option :value="0">— 不关联 —</option><option v-for="f in findings" :key="f.id" :value="f.id">AF-{{ f.id }} · {{ f.title }}</option></select></label>
          <label class="fld">所属组织<select v-model.number="ev.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showEvUpload = false">取消</button>
            <button class="btn" :disabled="!ev.name || !ev.file || (!ev.planId && !ev.findingId) || saving" @click="submitEvidence">{{ saving ? '上传中…' : '上传' }}</button>
          </div>
        </div>
      </div>

      <!-- 绑定检查表模板弹窗 -->
      <div v-if="showBind" class="modal-mask" @click.self="showBind = false">
        <div class="modal-card">
          <h3>绑定检查表模板</h3>
          <p class="muted" style="margin:-6px 0 12px">AP-{{ bindTarget && bindTarget.id }} · {{ bindTarget && bindTarget.title }}（模板须已在风险评估页上传 docx 表单）</p>
          <label class="fld">评估模板<select v-model.number="bindTemplateId">
            <option :value="0" disabled>— 选择模板 —</option>
            <option v-for="t in templates" :key="t.id" :value="t.id">#{{ t.id }} · {{ t.name }}</option>
          </select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showBind = false">取消</button>
            <button class="btn" :disabled="!bindTemplateId || saving" @click="submitBind">{{ saving ? '提交中…' : '确认绑定' }}</button>
          </div>
        </div>
      </div>

      <!-- 新建计划弹窗 -->
      <div v-if="showPlan" class="modal-mask" @click.self="showPlan = false">
        <div class="modal-card">
          <h3>新建审计计划</h3>
          <label class="fld">审计主题<input v-model="pf.title" placeholder="如 支付系统安全审计" /></label>
          <label class="fld">计划开始日<input type="date" v-model="pf.planStartDate" /></label>
          <label class="fld">所属组织<select v-model.number="pf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showPlan = false">取消</button>
            <button class="btn" :disabled="!pf.title || saving" @click="submitPlan">{{ saving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>

      <!-- 新建发现弹窗 -->
      <div v-if="showFinding" class="modal-mask" @click.self="showFinding = false">
        <div class="modal-card">
          <h3>新建审计发现</h3>
          <label class="fld">问题描述<input v-model="ff.title" placeholder="如 访问控制策略未落实最小授权" /></label>
          <label class="fld">严重度<select v-model="ff.severity">
            <option value="VERY_LOW">极低</option><option value="LOW">低</option><option value="MID">中</option><option value="HIGH">高</option>
          </select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showFinding = false">取消</button>
            <button class="btn" :disabled="!ff.title || saving" @click="submitFinding">{{ saving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>

      <!-- 下达整改弹窗 -->
      <div v-if="showRemed" class="modal-mask" @click.self="showRemed = false">
        <div class="modal-card">
          <h3>下达整改单</h3>
          <p class="muted" style="margin:-6px 0 12px">针对发现 AF-{{ remedTarget && remedTarget.id }}：{{ remedTarget && remedTarget.title }}</p>
          <label class="fld">责任人<input v-model="rf.assignee" placeholder="如 张三" /></label>
          <label class="fld">整改措施<input v-model="rf.measure" placeholder="如 配置最小授权策略并复核" /></label>
          <label class="fld">截止日期<input type="date" v-model="rf.dueDate" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showRemed = false">取消</button>
            <button class="btn" :disabled="!rf.assignee || saving" @click="submitRemed">{{ saving ? '提交中…' : '确认' }}</button>
          </div>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import { api } from '@/api/client.js'
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()
import { canWrite } from '@/auth.js'

const PLAN_LABEL = { PLANNED: '已计划', IN_PROGRESS: '实施中', REPORTING: '待签批', CLOSED: '已关闭', CANCELLED: '已取消' }
const PLAN_CLS = { PLANNED: 'wait', IN_PROGRESS: 'doing', REPORTING: 'wait', CLOSED: 'ok', CANCELLED: 'over' }
const SEV_LABEL = { VERY_LOW: '极低', LOW: '低', MID: '中', HIGH: '高' }
const SEV_CLS = { VERY_LOW: '', LOW: '', MID: 'm', HIGH: 'h' }
const REM_LABEL = { PENDING: '待开始', IN_PROGRESS: '整改中', SUBMITTED: '已提交', VERIFIED: '已验证', REJECTED: '已驳回' }
const REM_CLS = { PENDING: 'wait', IN_PROGRESS: 'doing', SUBMITTED: 'wait', VERIFIED: 'ok', REJECTED: 'over' }

const tab = ref('plan')
const plans = ref([])
const findings = ref([])
const remeds = ref([])
const planId = ref(0)
const findingId = ref(0)
const opMsg = ref('')
const opErr = ref('')
const saving = ref(false)

const inProgress = computed(() => plans.value.filter((p) => p.status === 'IN_PROGRESS').length)
const highFindings = computed(() => findings.value.filter((f) => f.severity === 'HIGH').length)

async function loadPlans() {
  try { plans.value = await api.get('/audit-plans?type=INTERNAL') } catch (e) { plans.value = [] }
}
async function loadFindings() {
  if (!planId.value) { findings.value = []; return }
  try { findings.value = await api.get('/audit-findings?auditPlanId=' + planId.value) } catch (e) { findings.value = [] }
}
async function loadRemed() {
  if (!findingId.value) { remeds.value = []; return }
  try { remeds.value = await api.get('/remediation-orders?findingId=' + findingId.value) } catch (e) { remeds.value = [] }
}
function pickPlan(p) { planId.value = p.id; tab.value = 'finding'; loadFindings() }
function pickFinding(f) { findingId.value = f.id; tab.value = 'remed'; loadRemed() }

async function planAction(p, action) {
  opMsg.value = ''; opErr.value = ''
  try { await api.post('/audit-plans/' + p.id + '/' + action, {}); opMsg.value = '已' + ({ start: '开始', report: '出具报告', close: '关闭', cancel: '取消' }[action]); await loadPlans(); setTimeout(() => (opMsg.value = ''), 2000) }
  catch (e) { opErr.value = e.message }
}
async function remAction(r, action) {
  opErr.value = ''
  try { await api.post('/remediation-orders/' + r.id + '/' + action, {}); await loadRemed() } catch (e) { opErr.value = e.message }
}

// ===== 检查表接表单引擎（V40）：绑定评估模板 → 执行生成评估 → 跳风险评估页填写 =====
const templates = ref([])
const showBind = ref(false)
const bindTarget = ref(null)
const bindTemplateId = ref(0)
async function openBind(p) {
  bindTarget.value = p; bindTemplateId.value = 0; opErr.value = ''
  try { templates.value = await api.get('/assessment-templates') } catch (e) { templates.value = [] }
  showBind.value = true
}
async function submitBind() {
  saving.value = true; opErr.value = ''
  try {
    await api.post('/audit-plans/' + bindTarget.value.id + '/checklist/bind', { templateId: bindTemplateId.value })
    showBind.value = false; await loadPlans()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}
async function startChecklist(p) {
  opErr.value = ''; opMsg.value = ''
  try {
    const saved = await api.post('/audit-plans/' + p.id + '/checklist/start', {})
    opMsg.value = '已生成检查表评估 #' + saved.checklistAssessmentId + '，到风险评估页填写'
    await loadPlans(); setTimeout(() => (opMsg.value = ''), 3000)
  } catch (e) { opErr.value = e.message }
}
function gotoChecklist(p) { window.location.hash = '#/risk' }

// ===== 证据库（V44）=====
const evidences = ref([])
const showEvUpload = ref(false)
const verifyResult = ref(null)
const ev = reactive({ name: '', planId: 0, findingId: 0, orgId: 12, file: null })
async function loadEvidence() {
  try { evidences.value = await api.get('/evidence') } catch (e) { evidences.value = [] }
}
function openEvUpload() {
  Object.assign(ev, { name: '', planId: planId.value || 0, findingId: 0, orgId: 12, file: null })
  opErr.value = ''; showEvUpload.value = true
}
function onEvFile(e) { ev.file = e.target.files && e.target.files[0] }
async function submitEvidence() {
  saving.value = true; opErr.value = ''
  try {
    const fd = new FormData()
    fd.append('file', ev.file)
    fd.append('name', ev.name)
    fd.append('orgId', ev.orgId)
    if (ev.planId) fd.append('planId', ev.planId)
    if (ev.findingId) fd.append('findingId', ev.findingId)
    await api.upload('/evidence', fd)
    showEvUpload.value = false; await loadEvidence()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}
async function verifyEv(e) {
  verifyResult.value = null
  try { verifyResult.value = await api.get('/evidence/' + e.id + '/verify') } catch (err) { opErr.value = err.message }
}
// 卷宗导出：zip = 卷宗 .docx（发现+整改+证据指纹清单）+ 证据原件（EV-{id}-原名，与指纹互为印证）
function exportDossier(p) { window.open('/api/audit-plans/' + p.id + '/dossier.zip', '_blank') }

// ===== 发现五要素 + 管理层回应（V47）=====
const detailTarget = ref(null)
const df = reactive({ conditionDesc: '', criteriaDesc: '', cause: '', effect: '', recommendation: '', response: '' })
function fmtDt(t) { return t ? new Date(t).toLocaleString() : '' }
function openDetail(f) {
  detailTarget.value = f
  Object.assign(df, {
    conditionDesc: f.conditionDesc || '', criteriaDesc: f.criteriaDesc || '', cause: f.cause || '',
    effect: f.effect || '', recommendation: f.recommendation || '', response: ''
  })
  opErr.value = ''
}
async function submitDetail() {
  saving.value = true; opErr.value = ''
  try {
    await api.put('/audit-findings/' + detailTarget.value.id + '/detail', {
      conditionDesc: df.conditionDesc || null, criteriaDesc: df.criteriaDesc || null,
      cause: df.cause || null, effect: df.effect || null, recommendation: df.recommendation || null
    })
    detailTarget.value = null; await loadFindings()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}
async function submitResponse() {
  saving.value = true; opErr.value = ''
  try {
    const saved = await api.post('/audit-findings/' + detailTarget.value.id + '/response', { response: df.response })
    detailTarget.value = saved; df.response = ''
    await loadFindings()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}

// ===== 年度计划 + follow-up（V52 · A3）=====
const annuals = ref([])
const annualId = ref(0)
const annualItems = ref([])
const showAnnualNew = ref(false)
const showAnnualItem = ref(false)
const af2 = reactive({ year: new Date().getFullYear(), title: '', orgId: 12 })
const aif = reactive({ target: '', riskRank: 3, quarter: 'Q1', note: '' })
const currentAnnual = computed(() => annuals.value.find((a) => a.id === annualId.value))
async function loadAnnual() {
  try { annuals.value = await api.get('/audit-annual') } catch (e) { annuals.value = [] }
}
async function loadAnnualItems() {
  annualItems.value = []
  if (!annualId.value) return
  try { annualItems.value = await api.get('/audit-annual/' + annualId.value + '/items') } catch (e) { annualItems.value = [] }
}
async function submitAnnual() {
  saving.value = true; opErr.value = ''
  try {
    const saved = await api.post('/audit-annual', { orgId: af2.orgId, year: af2.year, title: af2.title || null })
    showAnnualNew.value = false; await loadAnnual(); annualId.value = saved.id; await loadAnnualItems()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}
async function submitAnnualItem() {
  saving.value = true; opErr.value = ''
  try {
    await api.post('/audit-annual/' + annualId.value + '/items', { target: aif.target, riskRank: aif.riskRank, quarter: aif.quarter, note: aif.note || null })
    Object.assign(aif, { target: '', riskRank: 3, quarter: 'Q1', note: '' })
    showAnnualItem.value = false; await loadAnnualItems()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}
async function approveAnnual() {
  opErr.value = ''
  try { await api.post('/audit-annual/' + annualId.value + '/approve', {}); await loadAnnual() } catch (e) { opErr.value = e.message }
}
async function itemToPlan(it) {
  opErr.value = ''
  try { await api.post('/audit-annual/items/' + it.id + '/to-plan', {}); await loadAnnualItems(); await loadPlans() }
  catch (e) { opErr.value = e.message }
}
async function followUp(p) {
  opErr.value = ''; opMsg.value = ''
  try {
    const f = await api.post('/audit-plans/' + p.id + '/follow-up', {})
    opMsg.value = '已发起后续审计 AP-' + f.id
    await loadPlans(); setTimeout(() => (opMsg.value = ''), 3000)
  } catch (e) { opErr.value = e.message }
}

// ===== 审计通知书 + 程序底稿（V50 · A2）=====
const noticeTarget = ref(null)
const nf = reactive({ auditee: '', noticeScope: '', noticeBasis: '', auditTeam: '' })
function openNotice(p) {
  noticeTarget.value = p
  Object.assign(nf, { auditee: p.auditee || '', noticeScope: p.noticeScope || '', noticeBasis: p.noticeBasis || '', auditTeam: p.auditTeam || '' })
  opErr.value = ''
}
async function saveNotice(issue) {
  saving.value = true; opErr.value = ''
  try {
    await api.post('/audit-plans/' + noticeTarget.value.id + '/notice', {
      auditee: nf.auditee || null, noticeScope: nf.noticeScope || null,
      noticeBasis: nf.noticeBasis || null, auditTeam: nf.auditTeam || null, issue
    })
    noticeTarget.value = null; await loadPlans()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}

const PROC_LABEL = { PENDING: '待执行', DONE: '已执行', REVIEWED: '已复核' }
const PROC_CLS = { PENDING: 'wait', DONE: 'doing', REVIEWED: 'ok' }
const procPlanId = ref(0)
const procedures = ref([])
const showProcAdd = ref(false)
const pf2 = reactive({ name: '', objective: '' })
const procExecTarget = ref(null)
const procResult = ref('')
async function loadProcedures() {
  procedures.value = []
  if (!procPlanId.value) return
  try { procedures.value = await api.get('/audit-plans/' + procPlanId.value + '/procedures') } catch (e) { procedures.value = [] }
}
async function submitProc() {
  saving.value = true; opErr.value = ''
  try {
    await api.post('/audit-plans/' + procPlanId.value + '/procedures', { name: pf2.name, objective: pf2.objective || null })
    Object.assign(pf2, { name: '', objective: '' })
    showProcAdd.value = false; await loadProcedures()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}
function openProcExec(pr) { procExecTarget.value = pr; procResult.value = ''; opErr.value = '' }
async function submitProcExec() {
  saving.value = true; opErr.value = ''
  try {
    await api.post('/audit-plans/procedures/' + procExecTarget.value.id + '/execute', { result: procResult.value })
    procExecTarget.value = null; await loadProcedures()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}
async function reviewProc(pr) {
  opErr.value = ''
  try { await api.post('/audit-plans/procedures/' + pr.id + '/review', {}); await loadProcedures() }
  catch (e) { opErr.value = e.message }
}

// ===== 审计报告（V47：自动组稿 → 征求意见 → 定稿 → 签发）=====
const RPT_LABEL = { DRAFT: '草稿', COMMENTING: '征求意见中', FINAL: '已定稿', ISSUED: '已签发' }
const RPT_CLS = { DRAFT: 'wait', COMMENTING: 'doing', FINAL: 'wait', ISSUED: 'ok' }
const reportPlanId = ref(0)
const report = ref(null)
const rptSaving = ref(false)
const rptEdit = reactive({ title: '', opinion: null, summary: '', content: '' })
const rptFrozen = computed(() => !report.value || (report.value.status !== 'DRAFT' && report.value.status !== 'COMMENTING'))
async function loadReport() {
  report.value = null
  if (!reportPlanId.value) return
  try {
    const r = await api.get('/audit-reports?planId=' + reportPlanId.value)
    report.value = r && r.id ? r : null
    if (report.value) Object.assign(rptEdit, { title: r.title, opinion: r.opinion, summary: r.summary || '', content: r.content || '' })
  } catch (e) { report.value = null }
}
async function createReport() {
  opErr.value = ''
  try { await api.post('/audit-reports', { planId: reportPlanId.value }); await loadReport() }
  catch (e) { opErr.value = e.message }
}
async function saveReport() {
  rptSaving.value = true; opErr.value = ''
  try {
    await api.put('/audit-reports/' + report.value.id, {
      title: rptEdit.title, opinion: rptEdit.opinion || null, summary: rptEdit.summary || null, content: rptEdit.content || null
    })
    await loadReport()
  } catch (e) { opErr.value = e.message } finally { rptSaving.value = false }
}
async function rptAction(op) {
  opErr.value = ''
  try {
    // 流转前先落当前编辑（草稿/征求意见阶段），避免丢改动
    if (report.value.status === 'DRAFT' || report.value.status === 'COMMENTING') await saveReport()
    await api.post('/audit-reports/' + report.value.id + '/' + op, {})
    await loadReport()
  } catch (e) { opErr.value = e.message }
}

// 新建计划
const showPlan = ref(false)
const pf = reactive({ title: '', planStartDate: '', orgId: 12 })
function openPlan() { Object.assign(pf, { title: '', planStartDate: '', orgId: 12 }); opErr.value = ''; showPlan.value = true }
async function submitPlan() {
  saving.value = true; opErr.value = ''
  try {
    await api.post('/audit-plans', { orgId: pf.orgId, title: pf.title, auditType: 'INTERNAL', planStartDate: pf.planStartDate || null })
    showPlan.value = false; await loadPlans()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}

// 新建发现
const showFinding = ref(false)
const ff = reactive({ title: '', severity: 'MID' })
function openFinding() { Object.assign(ff, { title: '', severity: 'MID' }); opErr.value = ''; showFinding.value = true }
async function submitFinding() {
  saving.value = true; opErr.value = ''
  try {
    const plan = plans.value.find((p) => p.id === planId.value)
    await api.post('/audit-findings', { orgId: plan ? plan.orgId : 12, auditPlanId: planId.value, title: ff.title, severity: ff.severity })
    showFinding.value = false; await loadFindings()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}

// 下达整改
const showRemed = ref(false)
const remedTarget = ref(null)
const rf = reactive({ assignee: '', measure: '', dueDate: '' })
function openRemed(f) { remedTarget.value = f; findingId.value = f.id; Object.assign(rf, { assignee: '', measure: '', dueDate: '' }); opErr.value = ''; showRemed.value = true }
async function submitRemed() {
  saving.value = true; opErr.value = ''
  try {
    await api.post('/remediation-orders', { findingId: remedTarget.value.id, assignee: rf.assignee, dueDate: rf.dueDate || null, measure: rf.measure })
    showRemed.value = false; await loadRemed()
  } catch (e) { opErr.value = e.message } finally { saving.value = false }
}

onMounted(loadPlans)
</script>

<style scoped>
.phead { display: flex; align-items: center; margin-bottom: 14px; gap: 12px; }
.phead .kqt { font-size: 10.5px; letter-spacing: 1.5px; color: var(--accent); text-transform: uppercase; font-weight: 700; margin-bottom: 4px; }
.phead h1 { font-size: 20px; font-weight: 760; font-family: var(--font-display); }
.phead .sp { flex: 1; }
.btn { display: inline-flex; align-items: center; background: linear-gradient(135deg, var(--accent), var(--accent-strong)); color: #fff; border: 0; border-radius: var(--radius-md); padding: 8px 14px; font-size: 12.5px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-1); }
.btn.ghost { background: var(--bg); color: var(--text-2); border: 1px solid var(--surface-border); }
.btn.sm { padding: 5px 11px; font-size: 11.5px; }
.btn[disabled] { opacity: 0.55; cursor: not-allowed; }
.kpibar { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.kpibar .kc { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); padding: 14px 16px; box-shadow: var(--shadow-1); }
.kpibar .l { font-size: 11.5px; color: var(--text-3); margin-bottom: 6px; }
.kpibar .v { font-size: 22px; font-weight: 760; font-family: var(--font-display); }
.tabbar { display: flex; gap: 6px; margin-bottom: 14px; border-bottom: 1px solid var(--surface-border); }
.tabbar button { border: 0; background: none; color: var(--text-2); font-size: 13px; font-weight: 600; padding: 9px 14px; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; font-family: inherit; }
.tabbar button:hover { color: var(--text-1); }
.tabbar button.on { color: var(--accent-strong); border-bottom-color: var(--accent-strong); }
.card { background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-1); }
.ch { display: flex; align-items: center; gap: 10px; padding: 14px 18px 8px; }
.ch h3 { font-size: 14px; font-weight: 720; font-family: var(--font-display); }
.ch .cnt { font-size: 12px; font-weight: 700; color: var(--accent-strong); background: var(--accent-weak); border-radius: 999px; padding: 1px 9px; }
.ch .sel { margin-left: 10px; height: 32px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12.5px; }
.cb { padding: 14px 18px 18px; }
.hint { color: var(--text-3); font-size: 12.5px; padding: 22px 8px; text-align: center; }
table { width: 100%; border-collapse: collapse; }
thead th { text-align: left; font-size: 10.5px; font-weight: 600; color: var(--text-3); padding: 0 12px 10px; }
tbody td { padding: 9px 12px; border-top: 1px solid var(--border-subtle); font-size: 12px; }
tbody tr.clk { cursor: pointer; }
tbody tr.clk:hover, tbody tr.on { background: var(--accent-tint); }
.num { font-variant-numeric: tabular-nums; white-space: nowrap; }
.code { font-weight: 700; color: var(--accent-strong); font-family: var(--font-mono, monospace); font-size: 11.5px; }
.muted { color: var(--text-2); max-width: 240px; }
.pill { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: var(--info-tint); color: var(--info); }
.tag { display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 10.5px; font-weight: 600; background: rgba(120,120,120,.12); color: var(--text-2); }
.tag.m { background: var(--warning-tint); color: #a87d22; }
.tag.h { background: var(--danger-tint, rgba(180,35,45,.1)); color: var(--danger); }
.st { display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: var(--text-2); }
.st .d { width: 6px; height: 6px; border-radius: 50%; background: var(--text-3); }
.st.ok { color: var(--success); } .st.ok .d { background: var(--success); }
.st.doing { color: var(--accent-strong); } .st.doing .d { background: var(--accent-strong); }
.st.wait { color: #a87d22; } .st.wait .d { background: #a87d22; }
.st.over { color: var(--danger); } .st.over .d { background: var(--danger); }
.ops { white-space: nowrap; }
.mini { padding: 3px 9px; font-size: 11px; border: 1px solid var(--surface-border); background: var(--bg); color: var(--text-2); border-radius: 6px; cursor: pointer; margin-right: 4px; }
.mini:hover { background: var(--accent-tint); }
.mini.danger:hover { color: var(--danger); border-color: var(--danger); }
.emptyrow { text-align: center; color: var(--text-2); padding: 18px 0; }
.ok-msg { color: var(--success); font-weight: 600; font-size: 12px; margin: 10px 0 0; }
.cerr { color: var(--danger); font-size: 12.5px; margin: 8px 0 0; }
.note { font-size: 11.5px; color: var(--text-3); margin: 14px 2px 0; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.32); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-card { width: 440px; max-width: 92vw; background: var(--surface); border: 1px solid var(--surface-border); border-radius: var(--radius-lg); box-shadow: var(--shadow-2); padding: 22px 24px; }
.modal-card h3 { margin: 0 0 16px; font-size: 16px; }
.modal-card .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.modal-card .fld input, .modal-card .fld select { display: block; width: 100%; height: 38px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13.5px; font-family: inherit; outline: none; box-sizing: border-box; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
/* V47 五要素/审计报告 */
.modal-card.wide2 { width: 620px; max-height: 88vh; overflow-y: auto; }
.modal-card .fld textarea, .cb .fld textarea { display: block; width: 100%; margin-top: 5px; padding: 8px 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13px; font-family: inherit; line-height: 1.6; outline: none; box-sizing: border-box; resize: vertical; }
.resp-box { margin: 4px 0 12px; padding: 10px 12px; background: var(--bg); border: 1px dashed var(--border-subtle); border-radius: var(--radius-md); }
.resp-h { font-size: 11px; font-weight: 700; color: var(--text-3); margin-bottom: 6px; }
.resp-v { font-size: 12.5px; color: var(--text-1); line-height: 1.6; }
.resp-add { display: flex; gap: 8px; }
.resp-add input { flex: 1; height: 34px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--surface); color: var(--text-1); font-size: 12.5px; font-family: inherit; outline: none; }
.rpt-meta { display: grid; grid-template-columns: 2fr 1fr; gap: 12px; }
.cb .fld { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 12px; }
.cb .fld input, .cb .fld select { display: block; width: 100%; height: 36px; margin-top: 5px; padding: 0 11px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 13px; font-family: inherit; outline: none; box-sizing: border-box; }
.rpt-ta.mono { font-family: var(--font-mono, monospace); font-size: 12.5px; }
.rpt-issued { padding: 9px 12px; font-size: 12.5px; color: var(--success); background: var(--success-tint, rgba(40,150,90,.1)); border-left: 3px solid var(--success); border-radius: var(--radius-md); }
/* V44 证据库/反向取证 */
a.mini { text-decoration: none; display: inline-block; }
.verify-box { display: flex; flex-direction: column; gap: 4px; margin-top: 12px; padding: 10px 14px; border-radius: var(--radius-md); font-size: 12px; }
.verify-box.ok { background: var(--success-tint, rgba(40,150,90,.1)); color: var(--success); border-left: 3px solid var(--success); }
.verify-box.bad { background: var(--danger-tint); color: var(--danger); border-left: 3px solid var(--danger); }
.verify-box span { color: var(--text-2); }
</style>
