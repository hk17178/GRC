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
    submitting: 'Submitting…',
    confirm: 'Confirm',
    noPerm: 'Read-only: no permission'
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
    foot2: 'All access is audit-logged · MLPS L3 · Org-isolated',
    badCred: 'Invalid username or password',
    loginFail: 'Login failed: '
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
    perm: 'Permissions',
    approvalflow: 'Approval Flows',
    board: 'Board & Audit Trail',
    feedback: 'Feedback',
    settings: 'Settings'
  },

  // ---- topbar ----
  top: {
    searchPlaceholder: 'Search… (try TLS1.1)',
    logout: 'Logout'
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
    perm: { g: 'System', c: 'Permissions' },
    approvalflow: { g: 'System', c: 'Approval Flows' },
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
      th: { item: 'Report Item', regulator: 'Regulator', type: 'Type', deadline: 'Statutory Deadline', owner: 'Owner', status: 'Status', op: 'Action' },
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
      // ---- Wired to real backend (M11 CompliancePlan) ----
      list: 'Annual Plans',
      items: 'Plan Items',
      empty: 'No annual plans',
      itemEmpty: 'No items in this plan',
      selectHint: '← Select a plan to view its items',
      addItem: '＋ Add Item',
      th: { year: 'Year', title: 'Title', owner: 'Owner', status: 'Status', op: 'Action' },
      ith: { matter: 'Matter', dept: 'Owner Dept', due: 'Due', status: 'Status' },
      status: { DRAFT: 'Draft', ACTIVE: 'Active', CLOSED: 'Closed' },
      istatus: { PENDING: 'Pending', IN_PROGRESS: 'In Progress', DONE: 'Done' },
      op: { activate: 'Activate', close: 'Close' },
      create: { btn: '＋ New Plan' }
    },
    // ---- Real backend integration (DM-5) ----
    emptyRow: 'No data (live backend)',
    dash: '—',
    filingStatus: { TO_DRAFT: 'To Draft', DRAFTING: 'Drafting', PENDING_REVIEW: 'Pending Review', SUBMITTED: 'Submitted', CLOSED: 'Closed' },
    filingOp: { prepare: 'Prepare', submit: 'Submit', approve: 'Approve', reject: 'Reject', close: 'Close' },
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
      kri: 'KRI Monitoring',
      atv: 'A-T-V Modeling'
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
    assessStatus: { DRAFT: 'Draft', IN_PROGRESS: 'In Progress', PENDING_REVIEW: 'Pending Review', COMPLETED: 'Completed', CANCELLED: 'Cancelled' },
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
      tstatus: { DRAFT: 'Draft', PUBLISHED: 'Published', RETIRED: 'Retired' },
      fw: { MLPS: 'MLPS 2.0', ISO27001: 'ISO 27001', PCI_DSS: 'PCI DSS', PBOC: 'PBOC Payment' },
      f: { framework: 'Framework', desc: 'Description' },
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
      newCtrl: '＋ Define Control',
      noMap: 'Unmapped',
      empty: 'No controls',
      th: { id: 'ID', ctrl: 'Control', systems: 'Systems', reuse: 'Reuse', result: 'Result' },
      cstatus: { ACTIVE: 'Active', RETIRED: 'Retired' },
      f: { domain: 'Domain', domainPh: 'e.g. Access Control / Encryption / Logging' },
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
      kpi: { metrics: 'Metrics', breach: 'Breaches', critical: 'Critical', warning: 'Warning', normal: 'Normal', sources: 'Sources', sourcesSub: 'SIEM/Log/Vuln', collect: 'Collection', collectV: 'Normal' },
      title: 'KRI Metrics & Thresholds',
      config: '＋ Configure Metric',
      newKri: '＋ Define KRI',
      empty: 'No KRI metrics',
      th: { metric: 'Metric', owner: 'Owner', source: 'Source', current: 'Current', threshold: 'Warn/Crit Threshold', status: 'Status' },
      cstatus: { CRITICAL: 'Critical', WARNING: 'Warning', NORMAL: 'Normal', UNKNOWN: 'Unmeasured' },
      dir: { UPPER_BAD: 'Higher is worse', LOWER_BAD: 'Lower is worse' },
      f: { unit: 'Unit', direction: 'Threshold Direction', warn: 'Warning Threshold', crit: 'Critical Threshold' },
      rows: {
        vuln: { metric: 'Core system vuln fix time', source: 'Vuln Mgmt', current: '23.4 d', threshold: '≤15 d', status: 'Breach' },
        priv: { metric: 'Unreviewed privileged accounts', source: 'SIEM', current: '7', threshold: '=0', status: 'Critical' },
        log: { metric: 'Log retention days', source: 'Log Platform', current: '162 d', threshold: '≥180 d', status: 'Watch' }
      },
      st: { over: 'Breach', urgent: 'Critical', watch: 'Watch' }
    },
    ref: { code: 'Code', name: 'Name', owner: 'Owner', org: 'Organization' },
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

    scaffoldNote: 'KPI cards / heat matrix / remediation rates are live backend aggregates (per visible org); KRI sparklines and framework attainment remain prototype scaffolding.',
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
      title: 'Org × Domain Heatmap',
      sub: 'Open item counts (real data)',
      domain: {
        infosec: 'InfoSec',
        data: 'Data Compl.',
        continuity: 'Continuity',
        thirdparty: 'Third-party',
        reg: 'Regulatory',
        control: 'Internal Ctrl',
        risk: 'Risk Exposure',
        vendor: 'Third-party',
        audit: 'Audit Findings',
        remed: 'Remediation'
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
  },

  // ---- My Tasks (MyTasksView) ----
  todo: {
    tag: 'WORKBENCH · My Tasks',
    title: 'My Tasks',
    refresh: 'Refresh',
    listTitle: 'Org-wide Pending',
    empty: 'No tasks',
    myApprovals: 'My Approvals (by user)',
    myEmpty: 'No approvals assigned to me',
    th: { type: 'Type', matter: 'Item', due: 'Due', status: 'Status' },
    ath: { biz: 'Business', node: 'Node', role: 'Role Group', time: 'Arrived' },
    type: { REMEDIATION: 'Remediation', COMPLIANCE_ITEM: 'Compliance Item', REG_FILING: 'Reg Filing' },
    grp: {
      fill: 'To Fill', fillSub: 'Assessments in progress', fillEmpty: 'No assessments to fill', draft: 'Draft', filling: 'Filling',
      sign: 'To Sign', signSub: 'Effective policy sign-off', signEmpty: 'No policies to sign',
      remed: 'To Remediate', remedSub: 'Overdue red · <7d amber', remedEmpty: 'No remediation tasks', remedDefault: 'Remediation order'
    },
    due: { none: 'No deadline', overdue: 'Overdue {n}d', today: 'Due today', left: '{n}d left' }
  },

  // ---- Notifications (NotifyView) ----
  notify: {
    tag: 'WORKBENCH · Notifications',
    title: 'Notifications',
    refresh: 'Refresh',
    listTitle: 'Scheduled Reminders',
    empty: 'No notifications',
    th: { time: 'Time', event: 'Event', object: 'Object', threshold: 'Threshold', receipt: 'Receipt' },
    ack: 'Acknowledge',
    mergeTip: 'Multiple reminders for the same object & event merged (noise reduction)',
    digest: {
      tab: 'Digest', sub: 'Last {d} days by event type · unacknowledged count',
      d7: 'Last 7 days', d30: 'Last 30 days', d90: 'Last 90 days',
      event: 'Event Type', total: 'Reminders', unread: 'Unacked', empty: 'No reminders in this period.',
      aiHint: 'For a written management brief, use "Generate Management Brief" on the AI Assistant page (based on live stats; AI drafts require human review).'
    }
  },

  // ---- Regulation Tracking (RegulationView) ----
  reg: {
    tag: 'REGULATION · Tracking',
    title: 'Regulation Tracking',
    lib: 'Regulation Library',
    changes: 'Changes',
    empty: 'No regulations',
    changeEmpty: 'No changes for this regulation',
    selectHint: '← Select a regulation to view its changes',
    org: { pay: 'Payment Co.', consumer: 'Consumer Finance' },
    th: { code: 'Code', title: 'Title', issuer: 'Issuer', status: 'Status' },
    status: { TRACKING: 'Tracking', EFFECTIVE: 'Effective', SUPERSEDED: 'Superseded', ABOLISHED: 'Abolished' },
    cth: { type: 'Type', date: 'Date', desc: 'Description', impact: 'Impact', op: 'Action' },
    changeType: { ENACTED: 'Enacted', AMENDED: 'Amended', ABOLISHED: 'Abolished' },
    impact: { PENDING: 'Pending', ASSESSED: 'Assessed' },
    create: { btn: '＋ Add Regulation', category: 'Category', org: 'Organization', ok: 'Create' },
    change: { btn: '＋ Add Change', ok: 'Create' },
    assess: { btn: 'Assess Impact', title: 'Impact Assessment', scope: 'Affected Scope', scopePh: 'e.g. KYC policy, customer ID controls', note: 'Disposition', ok: 'Submit' }
  },

  // ---- Third-party Vendors (VendorView) ----
  vendor: {
    tag: 'VENDOR · Third-party',
    title: 'Third-party Vendors',
    list: 'Vendors',
    assessHist: 'Assessment History',
    empty: 'No vendors',
    assessEmpty: 'No assessments',
    selectHint: '← Select a vendor to view assessment history',
    unassessed: 'Unassessed',
    gateTip: 'Cannot activate without a risk assessment (onboarding gate)',
    org: { pay: 'Payment Co.', consumer: 'Consumer Finance' },
    th: { code: 'Code', name: 'Name', risk: 'Risk', status: 'Status', op: 'Action' },
    ath: { risk: 'Risk', score: 'Score', assessor: 'Assessor', concl: 'Conclusion' },
    status: { ONBOARDING: 'Onboarding', ACTIVE: 'Active', SUSPENDED: 'Suspended', TERMINATED: 'Terminated' },
    level: { VERY_HIGH: 'Very High', HIGH: 'High', MID: 'Mid', LOW: 'Low', VERY_LOW: 'Very Low' },
    op: { assess: 'Assess', activate: 'Activate', suspend: 'Suspend', reactivate: 'Reactivate' },
    create: { btn: '＋ Add Vendor', category: 'Service Category', criticality: 'Criticality', org: 'Organization', ok: 'Create' },
    assess: { title: 'Vendor Risk Assessment', risk: 'Risk Level', score: 'Score (0–100)', concl: 'Conclusion', ok: 'Submit' }
  },

  // ---- Compliance Checklist (ObligationView) ----
  obl: {
    tag: 'OBLIGATION · Checklist',
    title: 'Compliance Checklist',
    list: 'Obligations',
    empty: 'No obligations',
    org: { pay: 'Payment Co.', consumer: 'Consumer Finance' },
    th: { code: 'Code', title: 'Obligation', source: 'Source', dept: 'Owner Dept', due: 'Due', status: 'Status', op: 'Action' },
    status: { PENDING: 'Pending', IN_PROGRESS: 'In Progress', FULFILLED: 'Fulfilled', NON_COMPLIANT: 'Non-compliant' },
    op: { start: 'Start', fulfill: 'Mark Fulfilled', nc: 'Mark Non-compliant' },
    create: { btn: '＋ Add Obligation', source: 'Source', sourcePh: 'e.g. PBOC-AML-2026', org: 'Organization', ok: 'Create' },
    fulfill: { title: 'Mark Fulfilled', evidence: 'Evidence', evidencePh: 'e.g. monitoring system live, monthly report #2026-06', ok: 'Confirm' }
  },

  // ---- Feedback (FeedbackView) ----
  fb: {
    tag: 'FEEDBACK · Suggestions',
    title: 'Suggestions & Feedback',
    list: 'Feedback',
    empty: 'No feedback',
    org: { pay: 'Payment Co.', consumer: 'Consumer Finance' },
    th: { type: 'Type', title: 'Title', submitter: 'Submitter', handler: 'Handler', status: 'Status', op: 'Action' },
    type: { SUGGESTION: 'Suggestion', COMPLAINT: 'Complaint', BUG: 'Bug', QUESTION: 'Question' },
    status: { SUBMITTED: 'Submitted', IN_PROGRESS: 'In Progress', RESOLVED: 'Resolved', CLOSED: 'Closed', REJECTED: 'Rejected' },
    op: { triage: 'Triage', resolve: 'Resolve', close: 'Close', reject: 'Reject' },
    create: { btn: '＋ Submit Feedback', content: 'Content', org: 'Organization', ok: 'Submit' },
    triage: { handler: 'Handler', handlerPh: 'Assign handler, e.g. Compliance-Zhang' },
    resolve: { title: 'Resolve Feedback', resolution: 'Resolution', resolutionPh: 'Describe conclusion and actions' },
    view: { list: 'List', board: 'Board' },
    ob: {
      col: 'Outbound', submit: 'Outbound Reply', approve: 'Approve Outbound', reject: 'Reject Outbound',
      PENDING_APPROVAL: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected',
      title: 'Outbound Reply (approval required)', hint: 'The reply may only be sent externally after approval.',
      field: 'External reply draft', ph: 'Reply content…', ok: 'Submit for Approval'
    }
  },

  // ---- System Settings (SettingsView) ----
  set: {
    tag: 'SETTINGS · System',
    title: 'System Settings',
    list: 'Settings',
    empty: 'No settings',
    editableYes: 'Editable',
    locked: 'Locked',
    lockedDash: '—',
    lockTip: 'System-locked setting cannot be modified',
    org: { pay: 'Payment Co.', consumer: 'Consumer Finance' },
    th: { key: 'Key', category: 'Category', type: 'Type', value: 'Value', editable: 'Editable', op: 'Action' },
    op: { edit: 'Edit' },
    create: { btn: '＋ Define Setting', editable: 'Allow tenant edit', org: 'Organization', ok: 'Create' },
    edit: { title: 'Edit Value', value: 'New Value', ok: 'Save' }
  },

  // ---- Policy Publishing (PolicyView · M1) ----
  policy: {
    tag: 'POLICY · Publishing',
    title: 'Policy Publishing',
    list: 'Policies',
    empty: 'No policies',
    deprecated: 'Deprecated',
    org: { pay: 'Payment Co.', consumer: 'Consumer Finance' },
    th: { code: 'Code', title: 'Title', version: 'Version', status: 'Status', op: 'Action' },
    status: { DRAFT: 'Draft', REVIEW: 'In Review', EFFECTIVE: 'Effective', DEPRECATED: 'Deprecated' },
    op: { submit: 'Submit', approve: 'Approve', reject: 'Reject', signoff: 'Sign Off', archive: 'Archive' },
    create: { btn: '＋ New Policy', content: 'Content', org: 'Organization', ok: 'Create' },
    reject: { title: 'Reject Policy Review', reason: 'Reason', reasonPh: 'Explain rejection reason' }
  },

  // ---- Permission & Separation of Duties (PermissionView · M8) ----
  perm: {
    tag: 'PERMISSION · Access & SoD',
    title: 'Permission & SoD',
    sodRules: 'SoD Rules',
    sodRulesSub: 'system-level · mutually-exclusive role pairs',
    rth: { pair: 'Exclusive Pair', mode: 'Mode', desc: 'Description' },
    mode: { BLOCK: 'Hard Block', DETECT: 'Detective' },
    ruleDesc: { 1: 'Maker and Checker cannot be held by the same user in the same org (high-sensitivity, blocked at grant)', 2: 'Risk Owner and Auditor separation (detective, conflict logged pending exception)' },
    grantTitle: 'User Role Grants',
    user: 'User',
    query: 'Query',
    grantRole: 'Grant Role',
    active: 'Active',
    revoked: 'Revoked',
    noGrant: 'No grants for this user',
    gth: { role: 'Role', by: 'Granted By', state: 'State', op: 'Action' },
    op: { grant: 'Grant', revoke: 'Revoke', requestEx: 'Request Exception', approveEx: 'Approve', rejectEx: 'Reject' },
    exTitle: 'SoD Exceptions',
    exSub: 'request → approve',
    exRule: 'Conflict Rule',
    exReason: 'Reason',
    exReasonPh: 'Business necessity & compensating controls',
    exEmpty: 'No exception requests this session',
    exStatus: { PENDING: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected' },
    exth: { rule: 'Rule', user: 'User', status: 'Status', op: 'Action' },
    uar: {
      title: 'Access Review (UAR)',
      sub: 'periodic recertification',
      period: 'Period',
      reviewer: 'Reviewer',
      reviewerPh: 'e.g. Compliance-Li',
      org: 'Organization',
      create: 'New Review',
      batches: 'Review Batches',
      items: 'Review Items',
      empty: 'No review batches this session',
      itemEmpty: 'No items (active grants are snapshotted on start)',
      selectHint: '← Select a batch to view its items',
      grantRef: 'Grant #{id}',
      th: { period: 'Period', status: 'Status', op: 'Action' },
      ith: { grant: 'Grant', decision: 'Decision', op: 'Action' },
      status: { OPEN: 'Open', IN_REVIEW: 'In Review', COMPLETED: 'Completed' },
      decision: { PENDING: 'Pending', KEEP: 'Keep', REVOKE: 'Revoke' },
      op: { start: 'Start', complete: 'Complete', keep: 'Keep', revoke: 'Revoke' }
    }
  },

  // ---- AI Assistant (AiAssistantView · RAG) ----
  ai: {
    tag: 'AI · Assistant',
    title: 'AI Assistant',
    you: 'You',
    placeholder: 'Ask the knowledge base about policies, regulations, obligations. Answers cite their sources and use only ingested knowledge.',
    askPh: 'Type a question, Enter or click Ask…',
    ask: 'Ask',
    asking: 'Searching…',
    thinking: 'Retrieving & generating…',
    citations: 'Citations',
    mode: { local: 'Local Offline' },
    kb: 'Knowledge Base',
    kbEmpty: 'No knowledge yet — ingest policies/regulations',
    dth: { title: 'Title', type: 'Type', chunks: 'Chunks', status: 'Status' },
    stype: { POLICY: 'Policy', REGULATION: 'Regulation', OBLIGATION: 'Obligation', MANUAL: 'Manual' },
    dstatus: { PENDING: 'Pending', INDEXED: 'Indexed' },
    org: { pay: 'Payment Co.', consumer: 'Consumer Finance' },
    ingest: { btn: '＋ Ingest', ref: 'Source Ref', content: 'Content', contentPh: 'Paste policy/regulation text; auto-chunked & embedded', org: 'Organization', ok: 'Ingest & Index' },
    ds: {
      title: 'Data Source Coverage',
      d1: 'M1 Policies / M4 Regulations (KB {n} docs, semantic retrieval)',
      d2: 'M2 Risk / M3 Audit / M11 Regulatory (live stats, for material generation)',
      d3: 'Scope: only data of organizations visible to you (RLS)'
    },
    gen: {
      title: 'Generate Filing / Reporting Material', sub: 'Based on live compliance stats · AI drafts require human review',
      filing: 'Generate Filing Draft', brief: 'Generate Management Brief', busy: 'Generating…',
      briefLabel: 'Management Brief', filingLabel: 'Filing Draft', asOf: 'Data as of {t} · provider {p}',
      warn: '⚠ AI-generated draft — must be reviewed, supplemented and signed off before use; data reflects generation time.',
      hint: 'Use the buttons above to generate a draft from live stats of your visible organizations.',
      fail: 'Generation failed: '
    },
    chunks: {
      title: 'Chunks', chars: '{n} chars', loading: 'Loading…',
      empty: 'No chunks yet (indexing may be pending).', rowTip: 'Click to view chunks',
      delTip: 'Delete document & chunks', delConfirm: 'Delete knowledge source "{t}" and all its chunks?'
    }
  },

  // ---- Model Access (ModelAccessView) ----
  aimodel: {
    tag: 'MODEL · Access',
    title: 'Model Access',
    refresh: 'Refresh',
    current: 'Current AI Config',
    llm: 'LLM',
    embedding: 'Embedding',
    network: 'Data Egress',
    local: 'Local Offline · no LLM',
    embLocal: 'Local deterministic',
    noEgress: 'No egress (all local)',
    egress: 'Context sent to external LLM vendor',
    localNote: 'Local offline mode: vector retrieval + offline summary. No network, no key, no data egress. Returns retrieval summary only, not generative answers.',
    claudeNote: 'External LLM enabled: retrieved snippets and the question are sent to the configured vendor. Ensure data-egress & compliance (PIPL/MLPS).',
    howto: 'How to Switch',
    deploySide: 'deployment-side',
    howtoLead: 'Provider and key are set via deployment env vars (effective on restart), not entered here:',
    provClaude: 'External LLM (Anthropic protocol)',
    provOpenai: 'External LLM (OpenAI-compatible protocol; covers major cloud vendors and self-hosted inference)',
    keyDeploy: '(injected at deploy, not shown)',
    keyWarn: 'For security, the API key is injected only via deployment env vars. The UI provides no key-entry field and never echoes the key.',
    wl: {
      title: 'Model Whitelist', sub: 'Enforced when enabled entries exist',
      empty: 'No whitelist entries = enforcement off (any model can be saved).',
      add: 'Add to Whitelist', modelPh: 'Model id, e.g. qwen-plus', notePh: 'Note (optional)'
    },
    pt: {
      title: 'Prompt Templates', sub: 'Centralized system prompts',
      add: 'New Template', namePh: 'Template name, e.g. Clause-level Change Summary',
      edit: 'Edit', collapse: 'Collapse', save: 'Save Body'
    },
    gov: { enable: 'Enable', disable: 'Disable', del: 'Del', delConfirm: 'Delete "{t}"?' }
  },

  // ---- Approval Flow Designer (Vue Flow canvas) ----
  flow: {
    tag: 'APPROVAL FLOW · Designer',
    title: 'Approval Flow Designer',
    pickFlow: '— Pick flow —',
    new: '＋ New',
    palette: 'Nodes',
    props: 'Properties',
    save: 'Save',
    validate: 'Validate',
    publish: 'Publish',
    namePh: 'Flow name',
    selectHint: 'Click a node or edge on the canvas to edit',
    connectHint: 'Drag from a node’s right dot to the next node’s left dot to connect; click an edge to set a condition.',
    condHint: 'Condition uses JUEL, e.g. amount ge 100; empty = default branch.',
    delNode: 'Delete node',
    delEdge: 'Delete edge',
    edge: 'Edge',
    biz: { POLICY_PUBLISH: 'Policy Publish', RISK_ACCEPT: 'Risk Accept', SOD_EXCEPTION: 'SoD Exception', REG_FILING: 'Reg Filing' },
    status: { DRAFT: 'Draft', ACTIVE: 'Active', RETIRED: 'Retired' },
    node: { START: 'Start', APPROVAL: 'Approval', CONDITION: 'Condition', PARALLEL_SPLIT: 'Parallel Split', PARALLEL_JOIN: 'Parallel Join', END: 'End' },
    at: { ROLE: 'By Role', USER: 'By User', GROUP: 'By Group' },
    mode: { ANY: 'Any-of (N approve)', ALL: 'All-of (countersign)' },
    oc: { APPROVED: 'Approved', REJECTED: 'Rejected' },
    f: {
      name: 'Node name', approverType: 'Approver source', approvers: 'Approvers', approversPh: 'role/user, comma-separated, e.g. CHECKER,RISK_OWNER',
      mode: 'Completion', required: 'Required approvals', timeout: 'Timeout (h)', timeoutPh: '0 = off',
      escType: 'Escalate type', escRef: 'Escalate to', outcome: 'Outcome', condition: 'Condition', conditionPh: 'e.g. amount ge 100 (empty=default)'
    }
  },

  // ---- Permission Config (RbacConfigView · R5) ----
  rbac: {
    tag: 'RBAC · Permission Config',
    title: 'Permission Config',
    roles: 'Roles',
    matrix: 'Permission Matrix',
    newRole: '＋ New Role',
    create: 'Create',
    save: 'Save Matrix',
    super: 'Super',
    superNote: 'Super admins have read-write on all menus/buttons; no per-item config needed.',
    selectHint: '← Select a role to configure its menu/button permissions',
    resource: 'Resource (menu/button)',
    level: 'Level',
    roleCode: 'Role Code',
    roleName: 'Role Name',
    lv: { RW: 'R/W', RO: 'Read', HIDDEN: 'Hidden' }
  }
}
