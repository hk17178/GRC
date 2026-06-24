// =============================================================
// English locale (en)
// Note: every key here MUST mirror zh.js. Default strings are taken
// verbatim from the high-fidelity prototypes (login & dashboard).
// =============================================================
export default {
  // ---- common ----
  common: {
    brandName: 'Mandao GRC',
    brandSub: 'GOVERNANCE · RISK · COMPLIANCE',
    theme: 'Theme',
    signIn: 'SIGN IN',
    rememberMe: 'Remember me',
    forgotPwd: 'Forgot password?',
    password: 'Password',
    captcha: 'Captcha',
    cancel: 'Cancel',
    submitting: 'Submitting…'
  },

  // ---- five themes ----
  themes: {
    't-gov': 'Cinnabar',
    't-sand': 'Sandstone',
    't-glass': 'Clear Sky',
    't-emerald': 'Emerald',
    't-editorial': 'Editorial'
  },

  // ---- login ----
  login: {
    title: 'Group-wide<br>Governance · Risk · Compliance',
    slogan:
      'One platform, seeing through risk and obligations from the group down to every subsidiary — decisions grounded in data, exposure under real-time control, compliance value that compounds.',
    feat1Title: '5-Level Risk & KRI',
    feat1Desc: 'Asset-Threat-Vuln modeling, residual risk & acceptance',
    feat2Title: 'Audit & Regulatory Closure',
    feat2Desc:
      'Remediation → external reply → closure, statutory deadlines',
    feat3Title: 'AI Assistant (scoped)',
    feat3Desc:
      'Permission-scoped Q&A, self-assessment drafting & evidence trace',
    welcome: 'Welcome back',
    sub: 'Choose a sign-in method to access the platform',
    tabSso: 'Unified ID (AD)',
    tabLocal: 'Local Account',
    ssoHint: 'Corporate single sign-on — enter your AD domain credentials',
    domainAccount: 'Domain Account',
    // vue-i18n treats '@' as linked-message syntax; escape the '@' in the email via literal interpolation {'@'} or message compilation fails (blank page)
    domainPlaceholder: "DOMAIN\\username or user{'@'}corp.com",
    localAccount: 'Account / Email',
    localNote:
      'Local accounts are for break-glass / admin use when AD is unavailable; all logins are audited.',
    foot1: '© 2026 Mandao GRC · Authorized access only',
    foot2: 'All access is audit-logged · MLPS L3 · Org-isolated'
  },

  // ---- nav groups ----
  navGroup: {
    overview: 'Overview',
    business: 'Business',
    asset: 'Assets & Org',
    system: 'System'
  },

  // ---- nav items ----
  nav: {
    dashboard: 'Posture',
    todo: 'My Tasks',
    extaudit: 'External Audit',
    audit: 'Internal Audit',
    risk: 'Risk Assessment',
    law: 'Regulation Tracking',
    regaffairs: 'Regulatory Affairs',
    obligation: 'Obligations',
    policy: 'Policy System',
    ai: 'AI Assistant',
    vendor: 'Third-party Vendors',
    org: 'Org & Assets',
    notify: 'Notifications',
    aimodel: 'Model Access',
    perm: 'Permissions & Approval',
    board: 'Board & Audit Trail',
    feedback: 'Feedback',
    settings: 'Settings'
  },

  // ---- topbar ----
  top: {
    searchPlaceholder: 'Search… (try TLS1.1)'
  },

  // ---- dashboard shell (breadcrumb) ----
  dashboard: {
    crumb: 'Overview',
    crumbCurrent: 'Compliance Posture'
  },

  // ---- per-page breadcrumb (group / current), mirrors prototype crumb map ----
  crumb: {
    dashboard: { g: 'Overview', c: 'Compliance Posture' },
    extaudit: { g: 'Business', c: 'External Audit' },
    todo: { g: 'Overview', c: 'My Tasks' },
    audit: { g: 'Business', c: 'Internal Audit' },
    risk: { g: 'Business', c: 'Risk Assessment' },
    law: { g: 'Business', c: 'Regulation Tracking' },
    regaffairs: { g: 'Business', c: 'Regulatory Affairs' },
    obligation: { g: 'Business', c: 'Obligations' },
    policy: { g: 'Business', c: 'Policy System' },
    ai: { g: 'Business', c: 'AI Assistant' },
    vendor: { g: 'Business', c: 'Third-party Vendors' },
    org: { g: 'Assets & Org', c: 'Org & Assets' },
    notify: { g: 'System', c: 'Notifications' },
    aimodel: { g: 'System', c: 'Model Access' },
    perm: { g: 'System', c: 'Permissions & Approval' },
    board: { g: 'System', c: 'Board & Audit Trail' },
    feedback: { g: 'System', c: 'Feedback' },
    settings: { g: 'System', c: 'Settings' }
  },

  // ---- global floating AI assistant button ----
  aiFab: {
    title: 'AI Assistant'
  },

  // ---- placeholder page (menu items not yet restored) ----
  placeholder: {
    tag: 'Under Construction',
    title: '{name}',
    desc: 'This module prototype has not been restored yet. Navigation, theming and language switching all work normally.'
  },

  // ---- External Audit (M3-EXT, mirrors prototype #view-extaudit) ----
  extaudit: {
    tag: 'M3-EXT · External Audit',
    title: 'External Audit',
    register: '＋ Register Audit',
    seg: { all: 'Group', pay: 'PaySvc', consumer: 'Consumer Fin.', tech: 'DataTech' },
    tab: { tasks: 'Audit Tasks', findings: 'Audit Findings', remed: 'Remediation' },
    kpi: {
      active: 'Active Audits',
      bodies: 'Audit Bodies',
      openFindings: 'Open Findings',
      toRemed: 'To Remediate',
      certPassed: 'Certs Passed (Yr)'
    },
    tasks: {
      title: 'External Audit Tasks',
      sub: 'Org-isolated',
      th: {
        id: 'ID',
        cert: 'Certification',
        body: 'Audit Body',
        owner: 'Owner Unit',
        cycle: 'Cycle',
        planStart: 'Plan Start',
        status: 'Status'
      },
      status: {
        onsite: 'On-site Audit',
        pendingReply: 'Findings Pending',
        remediating: 'Remediating',
        awaitReg: 'Awaiting Regulator',
        passed: 'Passed'
      }
    },
    dist: { title: 'By Certification' },
    expiry: {
      title: 'Certs Expiring',
      pci: 'PCI DSS (PaySvc)',
      iso: 'ISO 27001 (Group)',
      mlps: 'MLPS L3 (PaySvc)',
      pciLeft: '28 days left',
      isoLeft: '73 days left',
      mlpsLeft: '120 days left'
    },
    remind: {
      title: 'Plan-Approach Reminder (WeChat)',
      sub: 'Auto-remind before plan start',
      leadDays: 'Lead days (configurable)',
      leadDaysV: '15d / 10d',
      bot: 'WeChat bot recipient',
      botV: 'Audit notification bot',
      task: 'EA-2026-07 MLPS Assessment',
      taskV: 'Plan 07-10 · 14d left',
      status: 'Status',
      statusV: 'Enabled'
    },
    findings: {
      title: 'External Audit Findings',
      sub: 'From External Bodies',
      th: {
        id: 'Finding ID',
        source: 'Source Audit',
        issue: 'Issue',
        cert: 'Certification',
        sev: 'Severity',
        owner: 'Owner Unit',
        status: 'Status'
      },
      issue: {
        chd: 'CHD not fully encrypted in transit',
        access: 'Access-review records incomplete',
        log: 'Reserve-fund log retention insufficient',
        baseline: 'Some host baselines non-compliant',
        privacy: 'Privacy notice needs improvement'
      },
      status: { remediating: 'Remediating', toRemed: 'To Remediate', verified: 'Verified' }
    },
    sevDist: {
      title: 'Findings by Severity (5)',
      vh: 'Very High',
      h: 'High',
      m: 'Medium',
      l: 'Low',
      vl: 'Very Low'
    },
    remTasks: {
      title: 'Remediation Tasks (w/ WeChat status)',
      th: {
        task: 'Task',
        source: 'Source Finding',
        owner: 'Owner',
        due: 'Due',
        notify: 'WeChat Notify',
        status: 'Status'
      },
      notify: { dueSoon: 'Notified · Due Soon', escalated: 'Escalated', notified: 'Notified' },
      status: { remediating: 'Remediating', overdue: 'Overdue', pending: 'Pending' }
    },
    funnel: {
      title: 'External Closure Funnel',
      external: 'External Findings',
      internalFix: 'Internal Fix',
      internalVerified: 'Internally Verified',
      submitted: 'Submitted to Body',
      accepted: 'Accepted by Body',
      closed: 'Closed by Body'
    },
    reply: {
      title: 'External Reply & Acceptance',
      th: { audit: 'Audit', report: 'Report', submitted: 'Submitted', accepted: 'Accepted by Body', conclusion: 'Conclusion' },
      accepted: { underReview: 'Under Review', accepted: 'Accepted' },
      conclusion: { pending: 'Pending', moreEvidence: 'More Evidence Req.', closed: 'Closed' }
    }
  },

  // ---- Regulatory Affairs (M11, mirrors cockpit prototype #view-regaffairs) ----
  regaffairs: {
    tag: 'Regulatory Affairs',
    title: 'Regulatory Affairs',
    register: '＋ Register Item',
    // register regulatory filing dialog
    create: { title: 'Register Regulatory Filing', item: 'Filing Item', itemPh: 'e.g. Payment Stats Report', regulator: 'Regulator', regulatorPh: 'e.g. PBOC', deadline: 'Statutory Deadline', org: 'Organization', orgPay: 'Payment Co.', orgConsumer: 'Consumer Finance', confirm: 'Register' },
    seg: { all: 'Group', pay: 'PaySvc', consumer: 'Consumer Finance' },
    kpi: {
      dueMonth: 'Due This Month',
      dueMonthSub: '2 statutory deadlines approaching',
      overdue: 'Overdue Reports',
      openInquiry: 'Open Inquiries',
      penaltyOpen: 'Open Penalty Fixes',
      majorReport: 'Major Incident Report',
      majorReportSub: 'This qtr'
    },
    tab: {
      calendar: 'Reporting Calendar',
      plan: 'Annual Compliance Plan',
      inquiry: 'Inquiries',
      penalty: 'Penalties & Talks',
      major: 'Major Incident Report'
    },
    calendar: {
      title: 'Reporting Calendar',
      sub: 'Statutory Deadline',
      th: { item: 'Report Item', regulator: 'Regulator', type: 'Type', deadline: 'Statutory Deadline', owner: 'Owner', status: 'Status' },
      itemPbocStat: 'Payment Business Statistical Report',
      itemAml: 'AML Report',
      itemPi: 'PI Protection Compliance Report',
      itemReserve: 'Reserve-Fund Custody Report',
      regPboc: 'PBOC',
      regAml: 'AML Center',
      regCac: 'CAC',
      typeMonthly: 'Monthly',
      typeQuarterly: 'Quarterly',
      typeAnnual: 'Annual',
      due0705: '07-05 (2 days left)',
      stDrafting: 'Drafting',
      stToDraft: 'To Draft',
      stSubmitted: 'Submitted',
      // Owners (avatar shows first letter)
      ownerChen: 'Chen Qiang',
      ownerLi: 'Li Na',
      ownerWang: 'Wang Fang',
      ownerZhang: 'Zhang Wei'
    },
    achieve: {
      title: 'Reporting (YTD)',
      onTime: 'On-time Rate',
      monthly: 'Monthly',
      quarterly: 'Quarterly'
    },
    upcoming: {
      title: 'Upcoming',
      d2: '2 days left',
      d7: '7 days left',
      d12: '12 days left'
    },
    inquiry: {
      title: 'Inquiries',
      add: '＋ Add Inquiry',
      th: { id: 'ID', regulator: 'Regulator', subject: 'Subject', received: 'Received', replyDue: 'Reply Due', replyLog: 'Reply Record', status: 'Status' },
      regPboc: 'PBOC',
      regCac: 'CAC',
      regNafr: 'NAFR',
      subjLargeTxn: 'Inquiry on large-transaction monitoring',
      subjPiExport: 'Statement on PI export',
      subjOutsource: 'Outsourcing risk management status',
      stReplyDrafting: 'Drafting Reply',
      stReplied: 'Replied · Logged',
      stAwaitFeedback: 'Awaiting Feedback',
      stClosed: 'Closed'
    },
    penalty: {
      title: 'Penalty & Talk Register',
      add: '＋ Register',
      th: { id: 'ID', type: 'Type', regulator: 'Regulator', reason: 'Reason', date: 'Date', remediation: 'Remediation Req.', replyStatus: 'Reply Status' },
      typeTalk: 'Talk',
      typePenalty: 'Penalty',
      regPboc: 'PBOC',
      regLocalPboc: 'Local PBOC',
      reasonKyc: 'Merchant KYC not fully implemented',
      reasonReserve: 'Reserve-fund management violation',
      remed30d: 'Remediate & report within 30 days',
      remedFine: 'Time-bound remediation + fine',
      stRemediating: 'Remediating · Reply Due',
      stRepliedClosed: 'Replied · Closed'
    },
    major: {
      info: 'Major risk/security incidents must be reported within regulatory deadlines (e.g., PBOC major-incident reporting); manage deadlines, versions and receipts here.',
      emptyTitle: 'No major incidents this quarter',
      emptyDesc: 'When a major incident occurs, file the regulatory report here and track the receipt; linked to incidents, remediation and evidence.',
      thSeverity: 'Severity',
      thOccurred: 'Occurred At',
      thReported: 'Reported At'
    },
    plan: {
      kpi: { total: 'Annual Items', done: 'Completed', doing: 'In Progress', overdue: 'Overdue' },
      title: '2026 Annual Compliance Plan',
      add: '＋ Add Item',
      th: { item: 'Item', category: 'Category', dept: 'Owner Dept.', planDone: 'Target', progress: 'Progress', status: 'Status' },
      itemMlps: 'MLPS L3 Assessment (PaySvc)',
      itemPiTrain: 'Company-wide PI Protection Training',
      itemAmlInspect: 'AML Special Inspection',
      itemDataSelf: 'Data Security Compliance Self-Assessment',
      catAssess: 'Assessment',
      catTrain: 'Training',
      catInspect: 'Inspection',
      catSelf: 'Self-Assess',
      deptInfosec: 'InfoSec Dept.',
      deptCompliance: 'Compliance Dept.',
      stDoing: 'In Progress',
      stDone: 'Completed',
      stOverdue: 'Overdue',
      // No matching backend entity (DM-5 class E): show placeholder, not fake data
      notImpl: 'Not yet implemented on the backend (scheduled for Stage 3)'
    },
    // ---- Real backend integration (DM-5) ----
    emptyRow: 'No data (live backend)',
    dash: '—',
    filingStatus: { TO_DRAFT: 'To Draft', DRAFTING: 'Drafting', SUBMITTED: 'Submitted', CLOSED: 'Closed' },
    inquiryStatus: { DRAFTING: 'Drafting Reply', REPLIED: 'Replied', AWAIT_FEEDBACK: 'Awaiting Feedback', CLOSED: 'Closed' },
    penaltyStatus: { OPEN: 'Open', RECTIFYING: 'Rectifying', CLOSED: 'Closed' },
    incidentStatus: { DRAFT: 'Draft', REPORTED: 'Reported', CLOSED: 'Closed' },
    severity: { VERY_LOW: 'Very Low', LOW: 'Low', MID: 'Medium', HIGH: 'High', VERY_HIGH: 'Very High' }
  },

  // ---- Risk Assessment (M2, mirrors cockpit prototype #view-risk and drill-downs) ----
  risk: {
    tag: 'M2 · Risk Assessment',
    title: 'Risk Assessment Loop',
    newAssess: '＋ New Assessment',
    // new-assessment dialog
    create: { obj: 'Assessment Object', objPh: 'e.g. Core Payment Gateway MLPS self-assessment', assessor: 'Assessor', period: 'Period', org: 'Organization', orgPay: 'Payment Co.', orgConsumer: 'Consumer Finance', confirm: 'Create' },
    // Risk findings · close-gate (CR-002 red-line)
    gate: {
      title: 'Risk Findings · Close-Gate',
      badge: 'CR-002 Red-line',
      scaffoldNote: 'The index / level distribution / risk-point list above are prototype visual scaffolding; the “Risk Findings · Close-Gate” below is live backend data with the CR-002 red-line.',
      th: { finding: 'Finding', inherent: 'Inherent', residual: 'Residual', acceptance: 'Acceptance', status: 'Status', ops: 'Actions' },
      missing: 'No acceptance · gated',
      gatedTip: 'High/Very-High residual risk must register a risk acceptance before it can be closed (CR-002 close-gate).',
      requestAccept: 'Request Acceptance',
      approveAccept: 'Approve',
      rejectAccept: 'Reject',
      close: 'Close',
      verify: 'Verify',
      verified: 'Verified',
      empty: 'No risk findings for this assessment yet',
      fstatus: { OPEN: 'Open', IN_TREATMENT: 'In Treatment', DONE: 'Closed', VERIFIED: 'Verified' },
      acceptTitle: 'Request Risk Acceptance',
      reason: 'Reason',
      reasonPh: 'e.g. business necessity + compensating controls, pending management approval',
      acceptConfirm: 'Submit Request'
    },
    tab: {
      tasks: 'Assessments',
      templates: 'Templates',
      controls: 'Control Library',
      kri: 'KRI Monitoring'
    },
    kpi: {
      active: 'In Progress',
      pending: 'Pending Approval',
      highRisk: 'High-Risk',
      overdue: 'Overdue',
      doneQuarter: 'Done This Qtr'
    },
    tasks: {
      title: 'Assessments',
      th: { id: 'ID', obj: 'Object', tpl: 'Template', prog: 'Progress', risk: 'Risk', due: 'Due', status: 'Status' },
      obj: { gateway: 'Core Payment Gateway', settle: 'Merchant Settlement', warehouse: 'Data Warehouse' },
      status: { filling: 'Filling', pending: 'Pending', live: 'Live' }
    },
    // 评估状态（对齐后端 AssessmentStatus）
    assessStatus: { DRAFT: 'Draft', IN_PROGRESS: 'In Progress', PENDING_REVIEW: 'Pending Review', COMPLETED: 'Completed' },
    levelDist: {
      title: 'Risk Level Distribution',
      sub: '5-tier',
      vh: 'Very High',
      h: 'High',
      m: 'Medium',
      l: 'Low',
      vl: 'Very Low'
    },
    funnel: {
      title: 'Assessment Funnel',
      started: 'Started',
      filling: 'Filling',
      pending: 'Pending',
      live: 'Live'
    },
    templates: {
      newTpl: 'New Template',
      ctrlPoints: 'controls',
      ctrlItems: 'controls',
      questions: 'items',
      cards: {
        mlps: { name: 'MLPS Level 3', desc: 'General + extended, organized by control ID', meta: '211 controls' },
        iso: { name: 'ISO 27001', desc: 'Asset-Threat-Vulnerability four factors', meta: '114 controls' },
        pci: { name: 'PCI DSS', desc: 'Cardholder data environment, 12 categories', meta: '78 controls' },
        pboc: { name: 'PBOC Payment', desc: 'Non-bank payment institution mapping', meta: '96 controls' },
        iso27701: { name: 'ISO 27701', desc: 'Privacy info management PIMS', meta: '49 controls' },
        vendor: { name: 'Vendor Assessment', desc: 'Contract terms + performance, quant + qual', meta: '54 items' },
        iso9001: { name: 'ISO 9001', desc: 'Quality management process approach', meta: '61 controls' }
      }
    },
    controls: {
      title: 'Unified Control Library',
      sub: 'control-point granularity',
      th: { id: 'ID', ctrl: 'Control', systems: 'Systems', reuse: 'Reuse', result: 'Result' },
      ctrl: { priv: 'Periodic privileged account review', tls: 'Data-in-transit encryption TLS1.2+', acl: 'Least-privilege access control' },
      result: { ok: 'Compliant', partial: 'Partially Non-compliant' },
      reuseTop: {
        title: 'Top Reused',
        acl: 'Least-privilege access',
        priv: 'Privileged review',
        log: 'Log retention'
      }
    },
    kri: {
      kpi: { metrics: 'Metrics', breach: 'Breaches', sources: 'Sources', sourcesSub: 'SIEM/Log/Vuln', collect: 'Collection', collectV: 'Normal' },
      title: 'KRI Metrics & Thresholds',
      config: '＋ Configure Metric',
      th: { metric: 'Metric', source: 'Source', current: 'Current', threshold: 'Threshold', status: 'Status' },
      rows: {
        vuln: { metric: 'Core system vuln fix time', source: 'Vuln Mgmt', current: '23.4 d', threshold: '≤15 d', status: 'Breach' },
        priv: { metric: 'Unreviewed privileged accounts', source: 'SIEM', current: '7', threshold: '=0', status: 'Critical' },
        log: { metric: 'Log retention days', source: 'Log Platform', current: '162 d', threshold: '≥180 d', status: 'Watch' }
      },
      st: { over: 'Breach', urgent: 'Critical', watch: 'Watch' }
    },
    // drill-down · assessment report (inherent/residual risk & management acceptance)
    report: {
      back: '← Back to Assessments',
      title: 'Merchant Settlement · Risk Assessment Report',
      pending: 'Pending Approval',
      exportPdf: 'Export PDF',
      sign: 'Review & Sign',
      kpi: { riskVal: 'Risk Value', high: 'High Risk', points: 'Risk Points', highPoints: 'High-Risk Points', toRemed: 'To Remediation' },
      list: {
        title: 'Risk Point List',
        th: { ctrl: 'Control', concl: 'Conclusion', level: 'Level', advice: 'Remediation Advice' },
        rows: {
          acl: { ctrl: 'Settlement API access control', concl: 'Shared accounts present', advice: 'Enable individual accounts + audit' },
          tls: { ctrl: 'Data-in-transit encryption', concl: 'Partial TLS1.1', advice: 'Upgrade to TLS1.2+' },
          log: { ctrl: 'Log retention', concl: 'Requirement met', advice: '—' }
        }
      },
      donut: { title: 'Level Distribution' },
      residual: {
        title: 'Risk Treatment & Residual Risk',
        th: { point: 'Risk Point', inherent: 'Inherent Risk', decision: 'Decision', measure: 'Treatment', residual: 'Residual Risk', accept: 'Owner Acceptance' },
        decision: { mitigate: 'Mitigate', accept: 'Accept' },
        accept: { pending: 'Pending Sign-off', accepted: 'Management Accepted' },
        rows: {
          shared: { point: 'Settlement API shared account', measure: 'Enable individual accounts + audit' },
          tls: { point: 'Partial TLS1.1', measure: 'Upgrade to TLS1.2+ in Q3' },
          backup: { point: 'Legacy backup media', measure: 'Time-boxed to next assessment review' }
        }
      }
    }
  },

  // ---- subsidiary names (shared by heatmap / remediation) ----
  'dash.sub.hq': 'Group HQ',
  'dash.sub.pay': 'Payment Co.',
  'dash.sub.consumer': 'Consumer Finance',
  'dash.sub.wealth': 'Wealth Mgmt.',
  'dash.sub.tech': 'Tech Co.',
  'dash.sub.factoring': 'Factoring Co.',

  // ---- compliance posture dashboard ----
  dash: {
    overviewTag: 'Group Overview · 2026 Q2',
    title: 'Compliance Posture',
    subtitle: 'Group-wide · as of 06-22 09:00 · near real-time',
    editLayout: 'Edit Layout',
    addWidget: 'Add Widget',
    overdue: 'Overdue {n}',
    seg: { all: 'Group', pay: 'Payment', consumer: 'Consumer Fin.', tech: 'Tech' },
    due: { pending: 'Pending {v}', overdue: 'Overdue {v}' },

    scaffoldNote: 'KPI cards above are live cross-module backend aggregates; the heat matrix / framework attainment below are prototype visual scaffolding (no backend aggregate yet).',
    // KPI cards (l=label, s=hint) — bound to /api/dashboard/summary live counts
    kpi: {
      openRisk: { l: 'Open Risk Findings', s: 'Risk assessment · in treatment' },
      gated: { l: 'Gated Findings', s: 'High residual · not released (CR-002)' },
      kriWarn: { l: 'KRI Warning', s: 'Warning threshold hit' },
      kriCrit: { l: 'KRI Critical', s: 'Critical threshold · red-line' },
      openAudit: { l: 'Open Audit Findings', s: 'Internal/external pending' },
      pendingFiling: { l: 'Pending Filings', s: 'To-draft / under review' },
      effPolicy: { l: 'Effective Policies', s: 'Published & effective' },
      pendingSod: { l: 'Pending SoD Waivers', s: 'Segregation-of-duties exceptions' }
    },

    // heatmap
    heat: {
      title: 'Subsidiary × Domain Heatmap',
      sub: 'Composite risk 0–100',
      domain: {
        infosec: 'InfoSec',
        data: 'Data Compl.',
        continuity: 'Continuity',
        thirdparty: 'Third-party',
        reg: 'Regulatory',
        control: 'Internal Ctrl'
      }
    },

    // remediation by subsidiary
    remed: { title: 'Remediation by Subsidiary' },

    // KRI monitoring
    kri: {
      title: 'KRI Monitoring',
      sub: 'SIEM/Log/Vuln',
      item: {
        vulnFix: { t: 'Critical Vuln Fix Time (days)', src: 'Vuln Mgmt · threshold ≤15', th: '+56% over' },
        privAcct: { t: 'Unreviewed Privileged Accts', src: 'SIEM · threshold =0', th: 'Critical' },
        logRetain: { t: 'Log Retention (days)', src: 'Log Platform · threshold ≥180', th: 'Met' },
        apiErr: { t: 'Third-party API Error Rate', src: 'SIEM · threshold ≤0.5%', th: 'Over' },
        exportApprove: { t: 'Data Export Approval Rate', src: 'Log Platform · threshold ≥99%', th: 'Normal' }
      }
    },

    // framework compliance
    frame: { title: 'Framework Compliance', sub: 'Control coverage' },

    // pending my approval
    approve: {
      title: 'Pending My Approval',
      all: 'All {n} →',
      type: { report: 'Report Sign-off', assess: 'Assess. Confirm', policy: 'Policy Release', reassess: 'Re-assess Review' },
      item: {
        mlps: { t: 'Payment Core MLPS Assessment Report', m: 'Qi Min · Security Lead' },
        consumerForm: { t: 'Consumer Finance Q2 Assessment Form', m: 'Wang Jianguo · Dept Head' },
        dataExit: { t: 'Release: Data Export Policy v2.5', m: 'Li Qiang · Policy Approver' },
        reassess: { t: 'Yunqing Tech Unplanned Re-assess Result', m: 'Chen Wei · Group Management' }
      }
    },

    // live event stream
    feed: {
      title: 'Live Event Stream',
      yesterday: 'Yest.',
      badge: { over: 'Overdue', kri: 'KRI', law: 'Reg.', aud: 'Audit', rev: 'Re-assess' },
      item: {
        overdue: 'Payment Co. "Privileged Account Review" remediation overdue 3 days, escalated to line manager',
        kri: 'Vuln fix time breached threshold (23.4>15 days), alert triggered and pushed to ops owner',
        law: 'PBOC released payment institution regulation rules, 4 policies flagged for review',
        audit: 'Consumer Finance Q2 compliance audit report signed off, 14 findings moved to remediation',
        review: 'Vendor "Yunqing Tech" reported data breach, unplanned re-assessment auto-triggered'
      }
    }
  },

  // ========================================================
  // Org & Assets page (OrgAssetView · M6)
  // Prototype #view-org: Org Structure / Asset Register / ROPA — 3 tabs
  // ========================================================
  orgasset: {
    tag: 'M6 · Org & Assets',
    title: 'Org & Asset Register',
    adSync: 'AD sync',
    register: '＋ Register Asset',
    tab: {
      org: 'Org Structure',
      asset: 'Asset Register',
      ropa: 'Processing Activities (ROPA)'
    },
    tree: {
      title: 'Org Tree (AD sync)',
      manualSync: 'Sync now',
      hq: 'Group HQ',
      payCo: 'PaySvc Subsidiary',
      payDepts: 'R&D / Ops / Risk depts',
      merchantCo: 'Merchant Svc Subsidiary',
      dataCo: 'DataTech Subsidiary',
      overseasCo: 'Overseas Subsidiary',
      people128: '128 people',
      people312: '312 people',
      people256: '256 people',
      people189: '189 people',
      people98: '98 people'
    },
    adStatus: {
      title: 'AD Sync Status',
      lastSync: 'Last Sync',
      lastSyncVal: 'Today 06:00',
      users: 'Users Synced',
      disabled: 'Disabled (Left)',
      deprovision: 'Deprovision SLA',
      deprovisionVal: '≤ sync cycle',
      status: 'Status',
      statusVal: 'Healthy'
    },
    kpi: {
      total: 'Total Assets',
      systems: 'Systems',
      highCrit: 'High Criticality',
      coverage: 'Assess. Coverage'
    },
    asset: {
      title: 'Asset Register',
      sub: 'with Data/Compliance Attrs',
      th: {
        id: 'ID',
        name: 'Name',
        type: 'Type',
        dataClass: 'Data Class.',
        pi: 'Personal Info',
        crossBorder: 'Cross-border',
        mlps: 'MLPS Level',
        chd: 'CHD',
        criticality: 'Criticality'
      },
      coreGw: 'Core Payment GW',
      merchantSettle: 'Merchant Settlement',
      crossClearing: 'Cross-border Clearing',
      dataWarehouse: 'Data Warehouse',
      yunqing: 'YunQing Tech',
      typeSystem: 'Systems',
      typeVendor: 'Vendor',
      sensitive: 'Sensitive',
      internal: 'Internal',
      yes: 'Yes',
      no: 'No',
      l3: 'L3',
      l2: 'L2',
      high: 'High',
      mid: 'Mid'
    },
    dist: {
      title: 'Asset Type Dist.',
      system: 'Systems',
      process: 'Processes',
      data: 'Data Assets',
      vendor: 'Vendors'
    },
    ropa: {
      title: 'Records of Processing (ROPA)',
      sub: 'PIPL statutory register',
      add: '＋ Add Activity',
      th: {
        activity: 'Activity',
        purpose: 'Purpose',
        piType: 'PI Type',
        volume: 'Volume',
        sensitive: 'Sensitive',
        export: 'Cross-border',
        retention: 'Retention',
        owner: 'Owner'
      },
      merchantSettle: 'Merchant settlement',
      crossClearing: 'Cross-border clearing',
      riskModel: 'Risk modeling',
      service: 'Customer follow-up',
      clearing: 'Clearing & settlement',
      crossPay: 'Cross-border payment',
      antiFraud: 'Anti-fraud',
      quality: 'Service quality',
      idCard: 'Identity/Card number',
      idTxn: 'Identity/Txn details',
      deviceBehavior: 'Device/Behavior',
      phoneCall: 'Phone/Call',
      vMillion: 'Millions',
      vHundredK: 'Hundred-thousands',
      vTenMillion: 'Tens of millions',
      y10: '10 years',
      y5: '5 years',
      y2: '2 years',
      yes: 'Yes',
      no: 'No',
      ownerLi: 'Li Na',
      ownerLiu: 'Liu Yang',
      ownerWang: 'Wang Fang',
      ownerChen: 'Chen Qiang'
    }
  },

  // ---- Board & Audit Trail (BoardView) ----
  board: {
    tag: 'AUDIT TRAIL · Tamper-proof',
    title: 'Board & Audit Trail',
    org: { group: 'Group', pay: 'Payment Co.', consumer: 'Consumer Finance' },
    verify: {
      title: 'Hash-chain Integrity Check',
      badge: 'Tamper-proof',
      org: 'Org chain',
      run: 'Verify',
      valid: 'Chain intact · {n} entries verified',
      broken: 'Broken/tampered · at seq {seq}'
    },
    trail: {
      title: 'Operation Trail',
      entityPh: 'Entity (e.g. POLICY:5)',
      actionPh: 'Action (e.g. POLICY_APPROVE)',
      actorPh: 'Actor',
      query: 'Query',
      empty: 'No trail (or adjust filters)',
      th: { seq: 'Seq', time: 'Time', action: 'Action', actor: 'Actor', entity: 'Entity', detail: 'Detail', hash: 'Chain hash' }
    }
  }
}
