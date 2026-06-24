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
    captcha: 'Captcha'
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

    // KPI cards (l=label, s=hint)
    kpi: {
      composite: { l: 'Composite Risk Index', s: 'Breakdown & attribution →' },
      highRisk: { l: 'Open High Risks', s: 'incl. 12 overdue remediation' },
      remedRate: { l: 'Remediation Rate', s: 'Closed 184/234 this qtr' },
      kriBreach: { l: 'KRI Breaches', s: '2 critical · SIEM/Log/Vuln' },
      signoff: { l: 'Policy Sign-off Rate', s: 'Core policies · 312 unconfirmed' },
      delivery: { l: 'Delivery Rate', s: '4,182 in 30d · 33 failed' },
      active: { l: 'Active Assess./Audits', s: 'Assess 14 · Audit 9' },
      vendorHigh: { l: 'High-Risk Vendors', s: '1 incident triggered re-assessment' }
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
  }
}
