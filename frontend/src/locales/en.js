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
    domainPlaceholder: 'DOMAIN\\username or user@corp.com',
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

  // ---- dashboard placeholder ----
  dashboard: {
    crumb: 'Overview',
    crumbCurrent: 'Compliance Posture',
    title: 'Compliance Posture',
    subtitle: 'Group-wide · as of 06-22 09:00 · near real-time',
    placeholder:
      'Dashboard placeholder — KPI cards, heat matrix and KRI monitors will be restored from the high-fidelity prototype.'
  }
}
