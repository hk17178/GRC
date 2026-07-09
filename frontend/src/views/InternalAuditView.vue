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
        <div><div class="kqt">{{ $t('intaudit.kqt') }}</div><h1>{{ $t('intaudit.title') }}</h1></div>
        <div class="sp"></div>
        <button v-if="tab === 'plan'" class="btn" :disabled="!canWrite('extaudit')"
                :title="canWrite('extaudit') ? '' : $t('intaudit.noWritePerm')" @click="openPlan">{{ $t('intaudit.newPlan') }}</button>
      </div>

      <!-- KPI -->
      <div class="kpibar">
        <div class="kc"><div class="l">{{ $t('intaudit.kpi.plans') }}</div><div class="v">{{ plans.length }}</div></div>
        <div class="kc"><div class="l">{{ $t('intaudit.kpi.inProgress') }}</div><div class="v" style="color:var(--accent-strong)">{{ inProgress }}</div></div>
        <div class="kc"><div class="l">{{ $t('intaudit.kpi.findings') }}</div><div class="v">{{ findings.length }}</div></div>
        <div class="kc"><div class="l">{{ $t('intaudit.kpi.highFindings') }}</div><div class="v" style="color:var(--danger)">{{ highFindings }}</div></div>
      </div>

      <div class="tabbar">
        <button :class="{ on: tab === 'annual' }" @click="tab = 'annual'; loadAnnual()">{{ $t('intaudit.tab.annual') }}</button>
        <button :class="{ on: tab === 'plan' }" @click="tab = 'plan'">{{ $t('intaudit.tab.plan') }}</button>
        <button :class="{ on: tab === 'finding' }" @click="tab = 'finding'">{{ $t('intaudit.tab.finding') }}</button>
        <button :class="{ on: tab === 'remed' }" @click="tab = 'remed'">{{ $t('intaudit.tab.remed') }}</button>
        <button :class="{ on: tab === 'proc' }" @click="tab = 'proc'; loadProcedures()">{{ $t('intaudit.tab.proc') }}</button>
        <button :class="{ on: tab === 'evidence' }" @click="tab = 'evidence'; loadEvidence()">{{ $t('intaudit.tab.evidence') }}</button>
        <button :class="{ on: tab === 'report' }" @click="tab = 'report'; loadReport()">{{ $t('intaudit.tab.report') }}</button>
      </div>

      <!-- 审计计划 -->
      <div v-show="tab === 'plan'" class="card">
        <div class="ch"><h3>{{ $t('intaudit.plan.title') }}</h3><span class="cnt">{{ plans.length }}</span></div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:720px">
            <thead><tr><th>{{ $t('intaudit.plan.th.id') }}</th><th>{{ $t('intaudit.plan.th.title') }}</th><th>{{ $t('intaudit.plan.th.type') }}</th><th>{{ $t('intaudit.plan.th.startDate') }}</th><th>{{ $t('intaudit.plan.th.status') }}</th><th>{{ $t('intaudit.plan.th.ops') }}</th></tr></thead>
            <tbody>
              <tr v-for="p in plans" :key="p.id" class="clk" :class="{ on: p.id === planId }" @click="pickPlan(p)">
                <td class="code">AP-{{ p.id }}</td>
                <td><b>{{ p.title }}</b></td>
                <td><span class="pill">{{ $t('intaudit.plan.typeInternal') }}</span></td>
                <td class="num">{{ p.planStartDate || '—' }}</td>
                <td><span class="st" :class="PLAN_CLS[p.status]"><span class="d"></span>{{ planLabel(p.status) }}</span></td>
                <td class="ops" @click.stop>
                  <!-- #2 操作列改下拉：按状态动态出可用操作，选中即执行 -->
                  <select class="opsel" :value="''" @change="dispatchPlanOp(p, $event)">
                    <option value="" disabled>{{ $t('intaudit.plan.opsPlaceholder') }}</option>
                    <template v-if="canWrite('extaudit')">
                      <option v-if="p.status==='PLANNED'" value="start">{{ $t('intaudit.plan.opStart') }}</option>
                      <option v-if="p.status==='IN_PROGRESS'" value="report">{{ $t('intaudit.plan.opReport') }}</option>
                      <option v-if="p.status==='REPORTING'" value="close">{{ $t('intaudit.plan.opClose') }}</option>
                      <option v-if="p.status==='PLANNED'||p.status==='IN_PROGRESS'" value="cancel">{{ $t('intaudit.plan.opCancel') }}</option>
                      <option v-if="!p.checklistTemplateId && !p.checklistAssessmentId" value="bind">{{ $t('intaudit.plan.opBind') }}</option>
                      <option v-else-if="!p.checklistAssessmentId" value="checklist">{{ $t('intaudit.plan.opChecklist') }}</option>
                      <option v-if="p.status==='CLOSED'" value="followup">{{ $t('intaudit.plan.opFollowup') }}</option>
                    </template>
                    <option v-if="p.checklistAssessmentId" value="gotoChecklist">{{ $t('intaudit.plan.opGotoChecklist', { id: p.checklistAssessmentId }) }}</option>
                    <option value="notice">{{ p.noticeIssuedAt ? $t('intaudit.plan.opNoticeIssued') : $t('intaudit.plan.opNotice') }}</option>
                    <option value="dossier">{{ $t('intaudit.plan.opDossier') }}</option>
                  </select>
                  <span v-if="p.followUpOf" class="pill" :title="$t('intaudit.plan.followUpTip')">↩ AP-{{ p.followUpOf }}</span>
                </td>
              </tr>
              <tr v-if="!plans.length"><td colspan="6" class="emptyrow">{{ $t('intaudit.plan.empty') }}</td></tr>
            </tbody>
          </table>
          <p v-if="opMsg" class="ok-msg">{{ opMsg }}</p>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
        </div>
      </div>

      <!-- 审计发现 -->
      <div v-show="tab === 'finding'" class="card">
        <div class="ch">
          <h3>{{ $t('intaudit.finding.title') }}</h3>
          <select class="sel" v-model.number="planId" @change="loadFindings">
            <option :value="0" disabled>{{ $t('intaudit.finding.selectPlan') }}</option>
            <option v-for="p in plans" :key="p.id" :value="p.id">AP-{{ p.id }} · {{ p.title }}</option>
          </select>
          <button v-if="planId && canWrite('extaudit')" class="btn sm" style="margin-left:auto" @click="openFinding">{{ $t('intaudit.finding.newBtn') }}</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <div v-if="!planId" class="hint">{{ $t('intaudit.finding.selectFirst') }}</div>
          <table v-else style="min-width:600px">
            <thead><tr><th>{{ $t('intaudit.finding.th.id') }}</th><th>{{ $t('intaudit.finding.th.problem') }}</th><th>{{ $t('intaudit.finding.th.severity') }}</th><th>{{ $t('intaudit.finding.th.ops') }}</th></tr></thead>
            <tbody>
              <tr v-for="f in findings" :key="f.id" class="clk" :class="{ on: f.id === findingId }" @click="pickFinding(f)">
                <td class="code">AF-{{ f.id }}</td>
                <td><b>{{ f.title }}</b></td>
                <td><span class="tag" :class="SEV_CLS[f.severity]">{{ sevLabel(f.severity) }}</span></td>
                <td class="ops" @click.stop>
                  <button class="mini" @click="openDetail(f)">{{ f.conditionDesc ? $t('intaudit.finding.fiveElementsDone') : $t('intaudit.finding.fiveElements') }}</button>
                  <!-- B33：发现行直接上传证据（预选 findingId）-->
                  <button v-if="canWrite('extaudit')" class="mini" @click="openEvUpload(f)">{{ $t('intaudit.finding.uploadEvidence') }}</button>
                  <button v-if="canWrite('extaudit')" class="mini" @click="openRemed(f)">{{ $t('intaudit.finding.issueRemed') }}</button>
                </td>
              </tr>
              <tr v-if="!findings.length"><td colspan="4" class="emptyrow">{{ $t('intaudit.finding.empty') }}</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 整改跟踪 -->
      <div v-show="tab === 'remed'" class="card">
        <div class="ch">
          <h3>{{ $t('intaudit.remed.title') }}</h3>
          <select class="sel" v-model.number="findingId" @change="loadRemed">
            <option :value="0" disabled>{{ $t('intaudit.remed.selectFinding') }}</option>
            <option v-for="f in findings" :key="f.id" :value="f.id">AF-{{ f.id }} · {{ f.title }}</option>
          </select>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <div v-if="!findingId" class="hint">{{ $t('intaudit.remed.pickFirst') }}</div>
          <table v-else style="min-width:640px">
            <thead><tr><th>{{ $t('intaudit.remed.th.id') }}</th><th>{{ $t('intaudit.remed.th.assignee') }}</th><th>{{ $t('intaudit.remed.th.measure') }}</th><th>{{ $t('intaudit.remed.th.due') }}</th><th>{{ $t('intaudit.remed.th.status') }}</th><th>{{ $t('intaudit.remed.th.ops') }}</th></tr></thead>
            <tbody>
              <tr v-for="r in remeds" :key="r.id">
                <td class="code">RO-{{ r.id }}</td>
                <td>{{ r.assignee || '—' }}</td>
                <td class="muted">{{ r.measure || '—' }}</td>
                <td class="num">{{ r.dueDate || '—' }}</td>
                <td><span class="st" :class="REM_CLS[r.status]"><span class="d"></span>{{ remLabel(r.status) }}</span></td>
                <td class="ops">
                  <template v-if="canWrite('extaudit')">
                    <button v-if="r.status==='PENDING'" class="mini" @click="remAction(r,'start')">{{ $t('intaudit.remed.start') }}</button>
                    <button v-if="r.status==='IN_PROGRESS'" class="mini" @click="remAction(r,'submit')">{{ $t('intaudit.remed.submit') }}</button>
                    <button v-if="r.status==='SUBMITTED'" class="mini" @click="remAction(r,'verify')">{{ $t('intaudit.remed.verify') }}</button>
                    <button v-if="r.status==='SUBMITTED'" class="mini danger" @click="remAction(r,'reject')">{{ $t('intaudit.remed.reject') }}</button>
                  </template>
                </td>
              </tr>
              <tr v-if="!remeds.length"><td colspan="6" class="emptyrow">{{ $t('intaudit.remed.empty') }}</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 年度计划（V52 · A3：直显列表——计划表 + 点行展开对象清单）-->
      <div v-show="tab === 'annual'" class="card">
        <div class="ch">
          <h3>{{ $t('intaudit.annual.title') }}</h3><span class="cnt">{{ annuals.length }}</span>
          <span class="sub">{{ $t('intaudit.annual.sub') }}</span>
          <button v-if="canWrite('extaudit')" class="btn sm" style="margin-left:auto" @click="showAnnualNew = true">{{ $t('intaudit.annual.newBtn') }}</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:760px">
            <thead><tr><th>{{ $t('intaudit.annual.th.year') }}</th><th>{{ $t('intaudit.annual.th.title') }}</th><th>{{ $t('intaudit.annual.th.status') }}</th><th>{{ $t('intaudit.annual.th.approve') }}</th><th>{{ $t('intaudit.annual.th.ops') }}</th></tr></thead>
            <tbody>
              <template v-for="a in annuals" :key="a.id">
                <tr class="clk" :class="{ on: a.id === annualId }" @click="toggleAnnual(a)">
                  <td class="code">{{ a.year }}</td>
                  <td><b>{{ a.title }}</b></td>
                  <td><span class="st" :class="a.status === 'APPROVED' ? 'ok' : 'wait'"><span class="d"></span>{{ a.status === 'APPROVED' ? $t('intaudit.annual.approved') : $t('intaudit.annual.draft') }}</span></td>
                  <td class="muted">{{ a.approvedBy ? (a.approvedBy + ' · ' + fmtDt(a.approvedAt)) : '—' }}</td>
                  <td class="ops" @click.stop>
                    <template v-if="canWrite('extaudit') && a.status === 'DRAFT'">
                      <button class="mini" @click="annualId = a.id; showAnnualItem = true">{{ $t('intaudit.annual.addItem') }}</button>
                      <button class="mini" @click="annualId = a.id; approveAnnual()">{{ $t('intaudit.annual.approve') }}</button>
                    </template>
                    <!-- 修复：td 上 @click.stop 会吞掉行点击，展开按钮须自带处理 -->
                    <button class="mini" @click="toggleAnnual(a)">{{ a.id === annualId ? $t('intaudit.annual.collapse') : $t('intaudit.annual.expand') }}</button>
                  </td>
                </tr>
                <tr v-if="a.id === annualId">
                  <td colspan="5" class="annual-sub">
                    <table style="width:100%">
                      <thead><tr><th>{{ $t('intaudit.annual.subTh.riskRank') }}</th><th>{{ $t('intaudit.annual.subTh.target') }}</th><th>{{ $t('intaudit.annual.subTh.quarter') }}</th><th>{{ $t('intaudit.annual.subTh.note') }}</th><th>{{ $t('intaudit.annual.subTh.plan') }}</th><th>{{ $t('intaudit.annual.subTh.ops') }}</th></tr></thead>
                      <tbody>
                        <tr v-for="it in annualItems" :key="it.id">
                          <td><span class="tag" :class="it.riskRank <= 2 ? 'h' : (it.riskRank === 3 ? 'm' : '')">#{{ it.riskRank }}</span></td>
                          <td><b>{{ it.target }}</b></td>
                          <td class="num">{{ it.quarter }}</td>
                          <td class="muted">{{ it.note || '—' }}</td>
                          <td><span v-if="it.planId" class="code">AP-{{ it.planId }}</span><span v-else class="muted">{{ $t('intaudit.annual.notPlanned') }}</span></td>
                          <td class="ops">
                            <button v-if="!it.planId && a.status==='APPROVED' && canWrite('extaudit')"
                                    class="mini" @click="itemToPlan(it)">{{ $t('intaudit.annual.toPlan') }}</button>
                          </td>
                        </tr>
                        <tr v-if="!annualItems.length"><td colspan="6" class="emptyrow">{{ $t('intaudit.annual.emptyItems') }}</td></tr>
                      </tbody>
                    </table>
                  </td>
                </tr>
              </template>
              <tr v-if="!annuals.length"><td colspan="5" class="emptyrow">{{ $t('intaudit.annual.empty') }}</td></tr>
            </tbody>
          </table>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
        </div>
      </div>

      <!-- 新建年度计划弹窗 -->
      <div v-if="showAnnualNew" class="modal-mask" @click.self="showAnnualNew = false">
        <div class="modal-card">
          <h3>{{ $t('intaudit.annual.newTitle') }}</h3>
          <label class="fld">{{ $t('intaudit.annual.fldYear') }}<input type="number" v-model.number="af2.year" /></label>
          <label class="fld">{{ $t('intaudit.annual.fldTitle', { year: af2.year }) }}<input v-model="af2.title" /></label>
          <label class="fld">{{ $t('intaudit.annual.fldOrg') }}<select v-model.number="af2.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAnnualNew = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!af2.year || saving" @click="submitAnnual">{{ saving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
      </div>

      <!-- 纳入审计对象弹窗 -->
      <div v-if="showAnnualItem" class="modal-mask" @click.self="showAnnualItem = false">
        <div class="modal-card">
          <h3>{{ $t('intaudit.annual.itemTitle') }}</h3>
          <label class="fld">{{ $t('intaudit.annual.fldTarget') }}<input v-model="aif.target" :placeholder="$t('intaudit.annual.targetPh')" /></label>
          <div class="fld-2col" style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
            <label class="fld">{{ $t('intaudit.annual.fldRiskRank') }}
              <select v-model.number="aif.riskRank"><option :value="1">1</option><option :value="2">2</option><option :value="3">3</option><option :value="4">4</option><option :value="5">5</option></select>
            </label>
            <label class="fld">{{ $t('intaudit.annual.fldQuarter') }}
              <select v-model="aif.quarter"><option>Q1</option><option>Q2</option><option>Q3</option><option>Q4</option></select>
            </label>
          </div>
          <label class="fld">{{ $t('intaudit.annual.fldNote') }}<input v-model="aif.note" :placeholder="$t('intaudit.annual.notePh')" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showAnnualItem = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!aif.target || saving" @click="submitAnnualItem">{{ saving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
      </div>

      <!-- 程序底稿（V50 · A2：审计程序 → 执行留底稿 → 复核）-->
      <div v-show="tab === 'proc'" class="card">
        <div class="ch">
          <h3>{{ $t('intaudit.proc.title') }}</h3>
          <select class="sel" v-model.number="procPlanId" @change="loadProcedures">
            <option :value="0" disabled>{{ $t('intaudit.report.selectPlan') }}</option>
            <option v-for="p in plans" :key="p.id" :value="p.id">AP-{{ p.id }} · {{ p.title }}</option>
          </select>
          <span class="sub">{{ $t('intaudit.proc.sub') }}</span>
          <button v-if="procPlanId && canWrite('extaudit')" class="btn sm" style="margin-left:auto" @click="showProcAdd = true">{{ $t('intaudit.proc.newBtn') }}</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <div v-if="!procPlanId" class="hint">{{ $t('intaudit.proc.hint') }}</div>
          <table v-else style="min-width:860px">
            <thead><tr><th>{{ $t('intaudit.proc.th.wpNo') }}</th><th>{{ $t('intaudit.proc.th.step') }}</th><th>{{ $t('intaudit.proc.th.objective') }}</th><th>{{ $t('intaudit.proc.th.record') }}</th><th>{{ $t('intaudit.proc.th.executor') }}</th><th>{{ $t('intaudit.proc.th.reviewer') }}</th><th>{{ $t('intaudit.proc.th.status') }}</th><th>{{ $t('intaudit.proc.th.ops') }}</th></tr></thead>
            <tbody>
              <tr v-for="pr in procedures" :key="pr.id">
                <td class="code">{{ pr.workpaperNo }}</td>
                <td><b>{{ pr.name }}</b></td>
                <td class="muted">{{ pr.objective || '—' }}</td>
                <td class="muted" style="max-width:260px">{{ pr.result || '—' }}</td>
                <td class="muted">{{ pr.executor || '—' }}</td>
                <td class="muted">{{ pr.reviewer || '—' }}</td>
                <td><span class="st" :class="PROC_CLS[pr.status]"><span class="d"></span>{{ procLabel(pr.status) }}</span></td>
                <td class="ops">
                  <template v-if="canWrite('extaudit')">
                    <button v-if="pr.status==='PENDING'" class="mini" @click="openProcExec(pr)">{{ $t('intaudit.proc.exec') }}</button>
                    <button v-if="pr.status==='DONE'" class="mini" @click="reviewProc(pr)">{{ $t('intaudit.proc.review') }}</button>
                  </template>
                </td>
              </tr>
              <tr v-if="!procedures.length"><td colspan="8" class="emptyrow">{{ $t('intaudit.proc.empty') }}</td></tr>
            </tbody>
          </table>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
        </div>
      </div>

      <!-- 报告模板管理弹窗（V54）-->
      <div v-if="showRptTpl" class="modal-mask" @click.self="showRptTpl = false">
        <div class="modal-card wide2" style="width:700px">
          <h3>{{ $t('intaudit.rptTpl.title') }}<span class="cnt" style="margin-left:8px">{{ rptTpls.length }}</span></h3>
          <p class="muted" style="margin:-8px 0 12px">{{ $t('intaudit.rptTpl.desc') }}</p>
          <div class="rtpl-list">
            <div v-for="t in rptTpls" :key="t.id" class="rtpl-item">
              <div class="gov-row">
                <b>{{ t.name }}</b><span class="pill" v-if="t.category">{{ t.category }}</span>
                <span class="gap" style="flex:1"></span>
                <template v-if="canWrite('extaudit')">
                  <button class="mini" @click="editRptTpl(t)">{{ rptTplEditId === t.id ? $t('intaudit.rptTpl.collapse') : $t('intaudit.rptTpl.edit') }}</button>
                  <button class="mini" @click="toggleRptTpl(t)">{{ t.enabled ? $t('intaudit.rptTpl.disable') : $t('intaudit.rptTpl.enable') }}</button>
                  <button class="mini danger" @click="delRptTpl(t)">{{ $t('intaudit.rptTpl.del') }}</button>
                </template>
              </div>
              <div v-if="rptTplEditId === t.id" style="margin:6px 0 10px">
                <textarea v-model="rptTplEditText" rows="10" class="rpt-ta mono" style="width:100%;box-sizing:border-box"></textarea>
                <button class="btn sm" style="margin-top:6px" @click="saveRptTpl(t)">{{ $t('intaudit.rptTpl.saveContent') }}</button>
              </div>
              <div v-else class="muted rtpl-clamp">{{ t.content }}</div>
            </div>
          </div>
          <div v-if="canWrite('extaudit')" class="gov-add" style="display:flex;gap:8px;margin-top:10px">
            <input v-model="rptTplNew.name" :placeholder="$t('intaudit.rptTpl.newNamePh')" style="flex:1" />
            <input v-model="rptTplNew.category" :placeholder="$t('intaudit.rptTpl.categoryPh')" style="width:140px" />
            <button class="btn sm" :disabled="!rptTplNew.name" @click="addRptTpl">{{ $t('intaudit.rptTpl.addBtn') }}</button>
          </div>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions"><button class="btn ghost" @click="showRptTpl = false">{{ $t('intaudit.rptTpl.close') }}</button></div>
        </div>
      </div>

      <!-- 通知书弹窗（V50：签发后冻结）-->
      <div v-if="noticeTarget" class="modal-mask" @click.self="noticeTarget = null">
        <div class="modal-card">
          <h3>{{ $t('intaudit.notice.title', { id: noticeTarget.id }) }}</h3>
          <p v-if="noticeTarget.noticeIssuedAt" class="rpt-issued" style="margin-bottom:12px">{{ $t('intaudit.notice.issued', { by: noticeTarget.noticeIssuedBy, at: fmtDt(noticeTarget.noticeIssuedAt) }) }}</p>
          <label class="fld">{{ $t('intaudit.notice.fldAuditee') }}<input v-model="nf.auditee" :disabled="!!noticeTarget.noticeIssuedAt" /></label>
          <label class="fld">{{ $t('intaudit.notice.fldScope') }}<textarea v-model="nf.noticeScope" rows="2" :disabled="!!noticeTarget.noticeIssuedAt"></textarea></label>
          <label class="fld">{{ $t('intaudit.notice.fldBasis') }}<input v-model="nf.noticeBasis" :disabled="!!noticeTarget.noticeIssuedAt" :placeholder="$t('intaudit.notice.basisPh')" /></label>
          <label class="fld">{{ $t('intaudit.notice.fldTeam') }}<input v-model="nf.auditTeam" :disabled="!!noticeTarget.noticeIssuedAt" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <a v-if="noticeTarget.noticeIssuedAt" class="btn ghost" :href="'/api/audit-plans/' + noticeTarget.id + '/notice.docx'" target="_blank" style="text-decoration:none">{{ $t('intaudit.notice.exportDocx') }}</a>
            <button class="btn ghost" @click="noticeTarget = null">{{ $t('intaudit.notice.close') }}</button>
            <template v-if="!noticeTarget.noticeIssuedAt && canWrite('extaudit')">
              <button class="btn ghost" :disabled="saving" @click="saveNotice(false)">{{ $t('intaudit.notice.saveDraft') }}</button>
              <button class="btn" :disabled="!nf.auditee || saving" @click="saveNotice(true)">{{ saving ? $t('common.submitting') : $t('intaudit.notice.issueBtn') }}</button>
            </template>
          </div>
        </div>
      </div>

      <!-- 新增程序弹窗 -->
      <div v-if="showProcAdd" class="modal-mask" @click.self="showProcAdd = false">
        <div class="modal-card">
          <h3>{{ $t('intaudit.proc.addTitle') }}</h3>
          <label class="fld">{{ $t('intaudit.proc.fldStep') }}<input v-model="pf2.name" :placeholder="$t('intaudit.proc.stepPh')" /></label>
          <label class="fld">{{ $t('intaudit.proc.fldObjective') }}<input v-model="pf2.objective" :placeholder="$t('intaudit.proc.objectivePh')" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showProcAdd = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!pf2.name || saving" @click="submitProc">{{ saving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
      </div>

      <!-- 执行程序（落底稿）弹窗 -->
      <div v-if="procExecTarget" class="modal-mask" @click.self="procExecTarget = null">
        <div class="modal-card wide2">
          <h3>{{ $t('intaudit.proc.execTitle', { wp: procExecTarget.workpaperNo }) }}</h3>
          <p class="muted" style="margin:-6px 0 12px">{{ procExecTarget.name }}<br/>{{ $t('intaudit.proc.execHint') }}</p>
          <label class="fld">{{ $t('intaudit.proc.fldRecord') }}<textarea v-model="procResult" rows="5" :placeholder="$t('intaudit.proc.recordPh')"></textarea></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="procExecTarget = null">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!procResult.trim() || saving" @click="submitProcExec">{{ saving ? $t('common.submitting') : $t('intaudit.proc.submitRecord') }}</button>
          </div>
        </div>
      </div>

      <!-- 证据库（V44：上传/反向取证/关联回溯）-->
      <div v-show="tab === 'evidence'" class="card">
        <div class="ch">
          <h3>{{ $t('intaudit.evidence.title') }}</h3><span class="cnt">{{ evidences.length }}</span>
          <span class="sub">{{ $t('intaudit.evidence.sub') }}</span>
          <button v-if="canWrite('extaudit')" class="btn sm" style="margin-left:auto" @click="openEvUpload">{{ $t('intaudit.evidence.uploadBtn') }}</button>
        </div>
        <div class="cb" style="overflow-x:auto;padding-top:0">
          <table style="min-width:860px">
            <thead><tr><th>{{ $t('intaudit.evidence.th.id') }}</th><th>{{ $t('intaudit.evidence.th.name') }}</th><th>{{ $t('intaudit.evidence.th.file') }}</th><th>{{ $t('intaudit.evidence.th.linked') }}</th><th>{{ $t('intaudit.evidence.th.fingerprint') }}</th><th>{{ $t('intaudit.evidence.th.upload') }}</th><th>{{ $t('intaudit.evidence.th.ops') }}</th></tr></thead>
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
                  <a class="mini" :href="'/api/evidence/' + e.id + '/download'" target="_blank">{{ $t('intaudit.evidence.download') }}</a>
                  <button class="mini" @click="verifyEv(e)">{{ $t('intaudit.evidence.verify') }}</button>
                </td>
              </tr>
              <tr v-if="!evidences.length"><td colspan="7" class="emptyrow">{{ $t('intaudit.evidence.empty') }}</td></tr>
            </tbody>
          </table>
          <div v-if="verifyResult" class="verify-box" :class="verifyResult.intact ? 'ok' : 'bad'">
            <b>{{ verifyResult.intact ? $t('intaudit.evidence.verifyOk') : $t('intaudit.evidence.verifyBad') }}</b>
            <span>EV-{{ verifyResult.evidenceId }} · {{ $t('intaudit.evidence.verifyDetail', { stored: verifyResult.storedSha256.slice(0, 16), actual: verifyResult.actualSha256.slice(0, 16) }) }}</span>
            <span v-if="verifyResult.planTitle">{{ $t('intaudit.evidence.tracePlan', { title: verifyResult.planTitle }) }}</span>
            <span v-if="verifyResult.findingTitle">{{ $t('intaudit.evidence.traceFinding', { title: verifyResult.findingTitle }) }}</span>
          </div>
        </div>
      </div>

      <!-- 审计报告（V47 · A1：自动组稿 → 征求意见 → 定稿(选意见) → 签发）-->
      <div v-show="tab === 'report'" class="card">
        <div class="ch">
          <h3>{{ $t('intaudit.report.title') }}</h3>
          <select class="sel" v-model.number="reportPlanId" @change="loadReport">
            <option :value="0" disabled>{{ $t('intaudit.report.selectPlan') }}</option>
            <option v-for="p in plans" :key="p.id" :value="p.id">AP-{{ p.id }} · {{ p.title }}</option>
          </select>
          <template v-if="reportPlanId">
            <span v-if="report" class="st" :class="RPT_CLS[report.status]" style="margin-left:8px"><span class="d"></span>{{ rptLabel(report.status) }}</span>
            <div style="margin-left:auto;display:flex;gap:8px" v-if="canWrite('extaudit')">
              <button class="btn ghost sm" @click="openRptTpl">{{ $t('intaudit.report.tplBtn') }}</button>
              <template v-if="!report">
                <select class="sel" v-model.number="rptTplId">
                  <option :value="0">{{ $t('intaudit.report.noTpl') }}</option>
                  <option v-for="t in rptTpls.filter(x => x.enabled)" :key="t.id" :value="t.id">{{ t.name }}</option>
                </select>
                <button class="btn sm" @click="createReport">{{ $t('intaudit.report.createDraft') }}</button>
              </template>
              <template v-else>
                <button v-if="report.status==='DRAFT'||report.status==='COMMENTING'" class="btn ghost sm" :disabled="rptSaving" @click="saveReport">{{ rptSaving ? $t('intaudit.report.saving') : $t('intaudit.report.save') }}</button>
                <button v-if="report.status==='DRAFT'" class="btn sm" @click="rptAction('comment')">{{ $t('intaudit.report.comment') }}</button>
                <button v-if="report.status==='COMMENTING'" class="btn sm" @click="rptAction('finalize')">{{ $t('intaudit.report.finalize') }}</button>
                <button v-if="report.status==='FINAL'" class="btn sm" @click="rptAction('issue')">{{ $t('intaudit.report.issue') }}</button>
              </template>
            </div>
          </template>
        </div>
        <div class="cb">
          <div v-if="!reportPlanId" class="hint">{{ $t('intaudit.report.hint') }}</div>
          <template v-else-if="report">
            <div class="rpt-meta">
              <label class="fld">{{ $t('intaudit.report.fldTitle') }}<input v-model="rptEdit.title" :disabled="rptFrozen" /></label>
              <label class="fld">{{ $t('intaudit.report.fldOpinion') }}
                <select v-model="rptEdit.opinion" :disabled="rptFrozen">
                  <option :value="null">{{ $t('intaudit.report.opinionUndecided') }}</option>
                  <option value="SATISFACTORY">{{ $t('intaudit.report.opinionSatisfactory') }}</option>
                  <option value="GENERALLY_SATISFACTORY">{{ $t('intaudit.report.opinionGenerally') }}</option>
                  <option value="NEEDS_IMPROVEMENT">{{ $t('intaudit.report.opinionNeedsImprovement') }}</option>
                  <option value="UNSATISFACTORY">{{ $t('intaudit.report.opinionUnsatisfactory') }}</option>
                </select>
              </label>
            </div>
            <label class="fld">{{ $t('intaudit.report.fldSummary') }}
              <textarea v-model="rptEdit.summary" rows="2" :disabled="rptFrozen" class="rpt-ta"></textarea>
            </label>
            <label class="fld">{{ $t('intaudit.report.fldContent') }}
              <textarea v-model="rptEdit.content" rows="14" :disabled="rptFrozen" class="rpt-ta mono"></textarea>
            </label>
            <div style="display:flex;gap:10px;align-items:center;margin-top:4px">
              <a class="btn ghost sm" :href="'/api/audit-reports/' + report.id + '/docx'" target="_blank" style="text-decoration:none">{{ $t('intaudit.report.exportDocx') }}</a>
              <div v-if="report.status==='ISSUED'" class="rpt-issued" style="flex:1">{{ $t('intaudit.report.issued', { by: report.issuedBy, at: fmtDt(report.issuedAt) }) }}</div>
            </div>
            <p v-if="opErr" class="cerr">{{ opErr }}</p>
          </template>
          <div v-else class="hint">{{ $t('intaudit.report.noReport') }}</div>
        </div>
      </div>

      <p class="note">{{ $t('intaudit.note') }}</p>

      <!-- 发现五要素弹窗（V47 · IIA 4C+R：现状/标准/原因/影响/建议 + 管理层回应）-->
      <div v-if="detailTarget" class="modal-mask" @click.self="detailTarget = null">
        <div class="modal-card wide2">
          <h3>{{ $t('intaudit.detail.title', { id: detailTarget.id }) }}<span class="muted" style="font-weight:400;font-size:12.5px;margin-left:8px">{{ detailTarget.title }}</span></h3>
          <label class="fld">{{ $t('intaudit.detail.condition') }}<textarea v-model="df.conditionDesc" rows="2"></textarea></label>
          <label class="fld">{{ $t('intaudit.detail.criteria') }}<textarea v-model="df.criteriaDesc" rows="2"></textarea></label>
          <label class="fld">{{ $t('intaudit.detail.cause') }}<textarea v-model="df.cause" rows="2"></textarea></label>
          <label class="fld">{{ $t('intaudit.detail.effect') }}<textarea v-model="df.effect" rows="2"></textarea></label>
          <label class="fld">{{ $t('intaudit.detail.recommendation') }}<textarea v-model="df.recommendation" rows="2"></textarea></label>
          <div class="resp-box">
            <div class="resp-h">{{ $t('intaudit.detail.mgmtResponse') }}</div>
            <div v-if="detailTarget.mgmtResponse" class="resp-v">{{ detailTarget.mgmtResponse }}<span class="muted">　— {{ detailTarget.responseBy }} · {{ fmtDt(detailTarget.responseAt) }}</span></div>
            <div v-else class="resp-add">
              <input v-model="df.response" :placeholder="$t('intaudit.detail.responsePh')" />
              <button class="btn ghost sm" :disabled="!df.response || saving" @click="submitResponse">{{ $t('intaudit.detail.submitResponse') }}</button>
            </div>
          </div>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="detailTarget = null">{{ $t('intaudit.detail.close') }}</button>
            <button v-if="canWrite('extaudit')" class="btn" :disabled="saving" @click="submitDetail">{{ saving ? $t('intaudit.detail.saving') : $t('intaudit.detail.saveElements') }}</button>
          </div>
        </div>
      </div>

      <!-- 上传证据弹窗 -->
      <div v-if="showEvUpload" class="modal-mask" @click.self="showEvUpload = false">
        <div class="modal-card">
          <h3>{{ $t('intaudit.evidence.uploadTitle') }}</h3>
          <label class="fld">{{ $t('intaudit.evidence.fldName') }}<input v-model="ev.name" :placeholder="$t('intaudit.evidence.namePh')" /></label>
          <label class="fld">{{ $t('intaudit.evidence.fldFile') }}<input type="file" @change="onEvFile" /></label>
          <label class="fld">{{ $t('intaudit.evidence.fldPlan') }}<select v-model.number="ev.planId"><option :value="0">{{ $t('intaudit.evidence.noLink') }}</option><option v-for="p in plans" :key="p.id" :value="p.id">AP-{{ p.id }} · {{ p.title }}</option></select></label>
          <label class="fld">{{ $t('intaudit.evidence.fldFinding') }}<select v-model.number="ev.findingId"><option :value="0">{{ $t('intaudit.evidence.noLink') }}</option><option v-for="f in findings" :key="f.id" :value="f.id">AF-{{ f.id }} · {{ f.title }}</option></select></label>
          <label class="fld">{{ $t('intaudit.evidence.fldOrg') }}<select v-model.number="ev.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showEvUpload = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!ev.name || !ev.file || (!ev.planId && !ev.findingId) || saving" @click="submitEvidence">{{ saving ? $t('intaudit.evidence.uploading') : $t('intaudit.evidence.upload') }}</button>
          </div>
        </div>
      </div>

      <!-- 绑定检查表模板弹窗 -->
      <div v-if="showBind" class="modal-mask" @click.self="showBind = false">
        <div class="modal-card">
          <h3>{{ $t('intaudit.bind.title') }}</h3>
          <p class="muted" style="margin:-6px 0 12px">{{ $t('intaudit.bind.hint', { id: (bindTarget && bindTarget.id), title: (bindTarget && bindTarget.title) }) }}</p>
          <label class="fld">{{ $t('intaudit.bind.fldTpl') }}<select v-model.number="bindTemplateId">
            <option :value="0" disabled>{{ $t('intaudit.bind.selectTpl') }}</option>
            <option v-for="t in templates" :key="t.id" :value="t.id">#{{ t.id }} · {{ t.name }}</option>
          </select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showBind = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!bindTemplateId || saving" @click="submitBind">{{ saving ? $t('common.submitting') : $t('intaudit.bind.confirmBind') }}</button>
          </div>
        </div>
      </div>

      <!-- 新建计划弹窗 -->
      <div v-if="showPlan" class="modal-mask" @click.self="showPlan = false">
        <div class="modal-card">
          <h3>{{ $t('intaudit.planForm.title') }}</h3>
          <label class="fld">{{ $t('intaudit.planForm.fldTopic') }}<input v-model="pf.title" :placeholder="$t('intaudit.planForm.topicPh')" /></label>
          <label class="fld">{{ $t('intaudit.planForm.fldStartDate') }}<input type="date" v-model="pf.planStartDate" /></label>
          <label class="fld">{{ $t('intaudit.planForm.fldOrg') }}<select v-model.number="pf.orgId"><option v-for="o in orgOptions" :key="o.id" :value="o.id">{{ orgLabel(o) }}</option></select></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showPlan = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!pf.title || saving" @click="submitPlan">{{ saving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
      </div>

      <!-- 新建发现弹窗 -->
      <div v-if="showFinding" class="modal-mask" @click.self="showFinding = false">
        <div class="modal-card">
          <h3>{{ $t('intaudit.findingForm.title') }}</h3>
          <label class="fld">{{ $t('intaudit.findingForm.fldProblem') }}<input v-model="ff.title" :placeholder="$t('intaudit.findingForm.problemPh')" /></label>
          <label class="fld">{{ $t('intaudit.findingForm.fldSeverity') }}<select v-model="ff.severity">
            <option value="VERY_LOW">{{ $t('intaudit.sev.VERY_LOW') }}</option><option value="LOW">{{ $t('intaudit.sev.LOW') }}</option><option value="MID">{{ $t('intaudit.sev.MID') }}</option><option value="HIGH">{{ $t('intaudit.sev.HIGH') }}</option>
          </select></label>
          <!-- B33：依据条款（引 M1 制度/准则）——写入五要素的「审计准则」，创建后可在五要素继续补全 -->
          <label class="fld">{{ $t('intaudit.findingForm.fldCriteria') }}<input v-model="ff.criteria" :placeholder="$t('intaudit.findingForm.criteriaPh')" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showFinding = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!ff.title || saving" @click="submitFinding">{{ saving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
        </div>
      </div>

      <!-- 下达整改弹窗 -->
      <div v-if="showRemed" class="modal-mask" @click.self="showRemed = false">
        <div class="modal-card">
          <h3>{{ $t('intaudit.remedForm.title') }}</h3>
          <p class="muted" style="margin:-6px 0 12px">{{ $t('intaudit.remedForm.hint', { id: (remedTarget && remedTarget.id), title: (remedTarget && remedTarget.title) }) }}</p>
          <label class="fld">{{ $t('intaudit.remedForm.fldAssignee') }}<input v-model="rf.assignee" :placeholder="$t('intaudit.remedForm.assigneePh')" /></label>
          <label class="fld">{{ $t('intaudit.remedForm.fldMeasure') }}<input v-model="rf.measure" :placeholder="$t('intaudit.remedForm.measurePh')" /></label>
          <label class="fld">{{ $t('intaudit.remedForm.fldDue') }}<input type="date" v-model="rf.dueDate" /></label>
          <p v-if="opErr" class="cerr">{{ opErr }}</p>
          <div class="modal-actions">
            <button class="btn ghost" @click="showRemed = false">{{ $t('common.cancel') }}</button>
            <button class="btn" :disabled="!rf.assignee || saving" @click="submitRemed">{{ saving ? $t('common.submitting') : $t('common.confirm') }}</button>
          </div>
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
import { confirm } from '@/composables/confirm'
import { useOrgs, orgLabel } from '@/orgs.js'
const orgOptions = useOrgs()
import { canWrite } from '@/auth.js'

const { t } = useI18n()

// 状态/严重度标签统一走 i18n（保留 *_CLS 样式映射不变）
const planLabel = (s) => t('intaudit.planStatus.' + s)
const sevLabel = (s) => t('intaudit.sev.' + s)
const remLabel = (s) => t('intaudit.remStatus.' + s)
const procLabel = (s) => t('intaudit.procStatus.' + s)
const rptLabel = (s) => t('intaudit.rptStatus.' + s)

const PLAN_CLS = { PLANNED: 'wait', IN_PROGRESS: 'doing', REPORTING: 'wait', CLOSED: 'ok', CANCELLED: 'over' }
const SEV_CLS = { VERY_LOW: '', LOW: '', MID: 'm', HIGH: 'h' }
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

// #2 操作下拉分发：选中即执行，选完复位（value 恒绑 ''）
function dispatchPlanOp(p, e) {
  const op = e.target.value
  e.target.value = ''
  if (!op) return
  if (op === 'bind') return openBind(p)
  if (op === 'checklist') return startChecklist(p)
  if (op === 'gotoChecklist') return gotoChecklist(p)
  if (op === 'notice') return openNotice(p)
  if (op === 'followup') return followUp(p)
  if (op === 'dossier') return exportDossier(p)
  return planAction(p, op)   // start / report / close / cancel
}

async function planAction(p, action) {
  opMsg.value = ''; opErr.value = ''
  try { await api.post('/audit-plans/' + p.id + '/' + action, {}); opMsg.value = t('intaudit.msg.plan' + action.charAt(0).toUpperCase() + action.slice(1)); await loadPlans(); setTimeout(() => (opMsg.value = ''), 2000) }
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
    opMsg.value = t('intaudit.msg.checklistGen', { id: saved.checklistAssessmentId })
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
function openEvUpload(finding) {
  // B33：从发现行进入时预选该发现（关联证据到 findingId）
  Object.assign(ev, {
    name: finding ? t('intaudit.evidence.defaultName', { id: finding.id }) : '',
    planId: finding ? 0 : (planId.value || 0),
    findingId: finding ? finding.id : 0,
    orgId: finding ? (finding.orgId || 12) : 12, file: null
  })
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
// 点行展开/收起对象清单（直显列表交互）
function toggleAnnual(a) {
  if (annualId.value === a.id) { annualId.value = 0; annualItems.value = [] }
  else { annualId.value = a.id; loadAnnualItems() }
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
    opMsg.value = t('intaudit.msg.followupDone', { id: f.id })
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
const RPT_CLS = { DRAFT: 'wait', COMMENTING: 'doing', FINAL: 'wait', ISSUED: 'ok' }
const reportPlanId = ref(0)
const report = ref(null)
const rptSaving = ref(false)
const rptEdit = reactive({ title: '', opinion: null, summary: '', content: '' })
const rptFrozen = computed(() => !report.value || (report.value.status !== 'DRAFT' && report.value.status !== 'COMMENTING'))
async function loadReport() {
  report.value = null
  if (!rptTpls.value.length) loadRptTpls()
  if (!reportPlanId.value) return
  try {
    const r = await api.get('/audit-reports?planId=' + reportPlanId.value)
    report.value = r && r.id ? r : null
    if (report.value) Object.assign(rptEdit, { title: r.title, opinion: r.opinion, summary: r.summary || '', content: r.content || '' })
  } catch (e) { report.value = null }
}
async function createReport() {
  opErr.value = ''
  try { await api.post('/audit-reports', { planId: reportPlanId.value, templateId: rptTplId.value || null }); await loadReport() }
  catch (e) { opErr.value = e.message }
}

// ===== 报告模板管理（V54）=====
const rptTpls = ref([])
const rptTplId = ref(0)
const showRptTpl = ref(false)
const rptTplEditId = ref(null)
const rptTplEditText = ref('')
const rptTplNew = reactive({ name: '', category: '' })
async function loadRptTpls() {
  try { rptTpls.value = await api.get('/audit-reports/templates') } catch (e) { rptTpls.value = [] }
}
function openRptTpl() { showRptTpl.value = true; loadRptTpls() }
function editRptTpl(t) {
  if (rptTplEditId.value === t.id) { rptTplEditId.value = null; return }
  rptTplEditId.value = t.id; rptTplEditText.value = t.content
}
async function saveRptTpl(t) {
  opErr.value = ''
  try {
    await api.put('/audit-reports/templates/' + t.id, { name: t.name, category: t.category, content: rptTplEditText.value })
    rptTplEditId.value = null; await loadRptTpls()
  } catch (e) { opErr.value = e.message }
}
async function toggleRptTpl(t) {
  try { await api.put('/audit-reports/templates/' + t.id + '/enabled?enabled=' + (!t.enabled)); await loadRptTpls() } catch (e) { opErr.value = e.message }
}
async function delRptTpl(tpl) {
  if (!await confirm(t('intaudit.rptTpl.confirmDel', { name: tpl.name }))) return
  try { await api.del('/audit-reports/templates/' + tpl.id); await loadRptTpls() } catch (e) { opErr.value = e.message }
}
async function addRptTpl() {
  opErr.value = ''
  try {
    await api.post('/audit-reports/templates', { orgId: 12, name: rptTplNew.name, category: rptTplNew.category || null, content: '一、审计概况\n\n二、审计发现与建议\n\n三、审计结论\n' })
    Object.assign(rptTplNew, { name: '', category: '' }); await loadRptTpls()
  } catch (e) { opErr.value = e.message }
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
const ff = reactive({ title: '', severity: 'MID', criteria: '' })
function openFinding() { Object.assign(ff, { title: '', severity: 'MID', criteria: '' }); opErr.value = ''; showFinding.value = true }
async function submitFinding() {
  saving.value = true; opErr.value = ''
  try {
    const plan = plans.value.find((p) => p.id === planId.value)
    const created = await api.post('/audit-findings', { orgId: plan ? plan.orgId : 12, auditPlanId: planId.value, title: ff.title, severity: ff.severity })
    // B33：依据条款写入五要素的「审计准则」（criteriaDesc），后续可在五要素继续补全其余四项
    if (ff.criteria && ff.criteria.trim() && created && created.id) {
      await api.put('/audit-findings/' + created.id + '/detail', { criteriaDesc: ff.criteria.trim() })
    }
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
/* #2 操作下拉 */
.opsel { height: 28px; padding: 0 8px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-2); font-size: 11.5px; font-family: inherit; outline: none; cursor: pointer; }
.opsel:hover { border-color: var(--accent); color: var(--accent-strong); }
/* V54 报告模板管理 */
.rtpl-list { max-height: 52vh; overflow-y: auto; }
.rtpl-item { border-bottom: 1px solid var(--border-subtle); padding: 6px 0; }
.rtpl-item .gov-row { display: flex; align-items: center; gap: 8px; font-size: 12.5px; }
.rtpl-clamp { font-size: 11.5px; line-height: 1.6; white-space: pre-wrap; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin: 4px 0 4px; }
.gov-add input { height: 32px; padding: 0 10px; border: 1px solid var(--surface-border); border-radius: var(--radius-md); background: var(--bg); color: var(--text-1); font-size: 12px; font-family: inherit; outline: none; }
.mini.danger:hover { color: var(--danger); border-color: var(--danger); }
/* V52 年度计划直显列表 */
.annual-sub { background: var(--bg); padding: 10px 16px 14px !important; }
.annual-sub thead th { font-size: 10px; padding: 6px 10px 6px; }
.annual-sub tbody td { background: var(--surface); }
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
