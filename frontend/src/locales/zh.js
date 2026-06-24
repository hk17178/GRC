// =============================================================
// 中文语言包（zh）
// 说明：所有界面文案集中维护，新增文案务必同步补充 en.js，杜绝硬编码。
// 文案默认值严格对齐高保真原型（登录页·5主题.html / 驾驶舱版.html）。
// =============================================================
export default {
  // ---- 通用 ----
  common: {
    brandName: 'Mandao GRC',
    brandSub: '治理 · 风险 · 合规',
    theme: '主题',
    signIn: '登 录',
    rememberMe: '记住我',
    forgotPwd: '忘记密码？',
    password: '密码',
    captcha: '验证码'
  },

  // ---- 五主题名称（与 tokens.css / 原型主题选择器一致）----
  themes: {
    't-gov': '朱砂',
    't-sand': '砂岩',
    't-glass': '晴空',
    't-emerald': '苍翠',
    't-editorial': '宣纸'
  },

  // ---- 登录页 ----
  login: {
    // 品牌侧（标题/标语为默认值，可被 localStorage 覆盖项替换）
    title: '集团一体化<br>治理 · 风险 · 合规平台',
    slogan:
      '一个平台，穿透集团至各子公司的风险底数与合规义务——让治理决策有据可依、风险敞口实时可控、合规价值持续沉淀。',
    feat1Title: '五级风险与 KRI 监控',
    feat1Desc: '资产-威胁-脆弱性建模、残余风险与管理层接受',
    feat2Title: '内外审与监管事项闭环',
    feat2Desc: '整改 → 对外回函 → 外方确认，报送日历与法定时限预警',
    feat3Title: 'AI 合规助手（不越权）',
    feat3Desc: '按权限过滤的智能问答、合规自评报告生成与举证溯源',
    // 表单侧
    welcome: '欢迎登录',
    sub: '请选择登录方式接入合规管理平台',
    tabSso: '统一身份登录(AD)',
    tabLocal: '本地账号',
    ssoHint: '企业统一身份认证，请输入您的 AD 域账号',
    domainAccount: '域账号',
    domainPlaceholder: 'DOMAIN\\用户名 或 user@corp.com',
    localAccount: '账号 / 企业邮箱',
    localNote:
      '本地账号仅用于 AD 不可用时的应急 / 管理员登录，登录行为将被审计留痕',
    foot1: '© 2026 Mandao GRC 集团合规管理平台 · 仅供授权用户访问',
    foot2: '访问行为将被审计留痕 · 等保三级 · 数据按组织隔离'
  },

  // ---- 左侧导航分组（信息架构对齐驾驶舱版原型）----
  navGroup: {
    overview: '概览',
    business: '业务',
    asset: '资产与组织',
    system: '系统管理'
  },

  // ---- 左侧导航菜单项 ----
  nav: {
    dashboard: '合规态势',
    todo: '我的待办',
    extaudit: '外部审计',
    audit: '内部审计',
    risk: '风险评估',
    law: '法规跟踪',
    regaffairs: '监管事项',
    obligation: '合规清单',
    policy: '制度体系',
    ai: 'AI 智能问答',
    vendor: '第三方供应商',
    org: '组织与资产',
    notify: '通知中心',
    aimodel: '模型接入',
    perm: '权限与审批',
    board: '看板与留痕',
    feedback: '建议与反馈',
    settings: '系统设置'
  },

  // ---- 顶栏 ----
  top: {
    searchPlaceholder: '搜索…（试试 TLS1.1）'
  },

  // ---- 仪表盘占位页 ----
  dashboard: {
    crumb: '概览',
    crumbCurrent: '合规态势总览',
    title: '合规态势',
    subtitle: '全集团 · 数据截至 06-22 09:00 · 准实时',
    placeholder:
      '仪表盘内容占位 —— 后续将按高保真原型「驾驶舱版」逐步复原 KPI、热力矩阵、KRI 监控等组件。'
  }
}
