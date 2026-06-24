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
    // vue-i18n 把 '@' 视为链接消息语法，邮箱里的 '@' 须用字面插值 {'@'} 转义，否则消息编译失败导致整页空白
    domainPlaceholder: "DOMAIN\\用户名 或 user{'@'}corp.com",
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

  // ---- 仪表盘外壳（面包屑等）----
  dashboard: {
    crumb: '概览',
    crumbCurrent: '合规态势总览'
  },

  // ---- 子公司名（热力矩阵 / 整改率共用）----
  'dash.sub.hq': '集团总部',
  'dash.sub.pay': '支付子公司',
  'dash.sub.consumer': '消费金融',
  'dash.sub.wealth': '财富管理',
  'dash.sub.tech': '数科子公司',
  'dash.sub.factoring': '保理子公司',

  // ---- 驾驶舱主页 ----
  dash: {
    overviewTag: 'Group Overview · 2026 Q2',
    title: '合规态势',
    subtitle: '全集团 · 数据截至 06-22 09:00 · 准实时',
    editLayout: '编辑布局',
    addWidget: '添加组件',
    overdue: '逾期{n}',
    seg: { all: '全集团', pay: '支付科技', consumer: '消费金融', tech: '数科' },
    due: { pending: '待处理 {v}', overdue: '逾期 {v}' },

    // KPI 指标卡（l=标题, s=辅助说明）
    kpi: {
      composite: { l: '综合风险指数', s: '构成与归因 →' },
      highRisk: { l: '高风险项(未关闭)', s: '含逾期整改 12 项' },
      remedRate: { l: '整改完成率', s: '本季关闭 184/234' },
      kriBreach: { l: 'KRI 超阈值', s: '2 紧急·SIEM/日志/漏洞' },
      signoff: { l: '制度签署确认率', s: '核心制度·未确认 312 人' },
      delivery: { l: '通知送达率', s: '近30天4,182条·失败33' },
      active: { l: '进行中评估/审计', s: '评估 14 · 审计 9' },
      vendorHigh: { l: '供应商高风险', s: '1 起事件触发复评' }
    },

    // 热力矩阵
    heat: {
      title: '子公司 × 风险域 · 热力矩阵',
      sub: '综合风险值 0–100',
      domain: {
        infosec: '信息安全',
        data: '数据合规',
        continuity: '业务连续',
        thirdparty: '第三方',
        reg: '监管合规',
        control: '内控'
      }
    },

    // 整改完成率 · 分子公司
    remed: { title: '整改完成率 · 分子公司' },

    // KRI 持续监控
    kri: {
      title: 'KRI 持续监控',
      sub: 'SIEM/日志/漏洞',
      item: {
        vulnFix: { t: '高危漏洞修复时效(天)', src: '漏洞管理 · 阈值≤15', th: '超标 56%' },
        privAcct: { t: '特权账号未审查数', src: 'SIEM · 阈值=0', th: '紧急' },
        logRetain: { t: '日志留存周期(天)', src: '日志平台 · 阈值≥180', th: '达标' },
        apiErr: { t: '第三方接口异常率', src: 'SIEM · 阈值≤0.5%', th: '超标' },
        exportApprove: { t: '数据导出审批合规率', src: '日志平台 · 阈值≥99%', th: '正常' }
      }
    },

    // 体系合规达成度
    frame: { title: '体系合规达成度', sub: '控制点覆盖率' },

    // 待我审批
    approve: {
      title: '待我审批',
      all: '全部 {n} →',
      type: { report: '报告签批', assess: '评估确认', policy: '制度发布', reassess: '复评审批' },
      item: {
        mlps: { t: '支付核心系统等保测评报告', m: '起敏 · 信息安全负责人' },
        consumerForm: { t: '消费金融季度评估表单确认', m: '王建国 · 部门负责人' },
        dataExit: { t: '《数据出境管理办法 v2.5》发布', m: '李强 · 制度发布审批人' },
        reassess: { t: '云擎科技非计划性复评结论', m: '陈伟 · 集团管理层' }
      }
    },

    // 实时事件流
    feed: {
      title: '重点关注 · 实时事件流',
      yesterday: '昨日',
      badge: { over: '逾期', kri: 'KRI', law: '法规', aud: '审计', rev: '复评' },
      item: {
        overdue: '支付子公司「特权账号定期审查」整改逾期 3 天，已升级至直属上级',
        kri: '漏洞修复时效超阈值(23.4>15 天)，触发预警并推送运维负责人',
        law: '人民银行发布支付机构监管条例实施细则，命中 4 条制度待复核',
        audit: '消费金融 Q2 合规审计报告完成签批，发现 14 项已转整改',
        review: '供应商「云擎科技」被曝数据泄露，自动触发非计划性复评任务'
      }
    }
  }
}
