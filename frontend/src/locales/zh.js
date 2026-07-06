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
    captcha: '验证码',
    cancel: '取消',
    submitting: '提交中…',
    confirm: '确认',
    noPerm: '只读：无操作权限'
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
    foot2: '访问行为将被审计留痕 · 等保三级 · 数据按组织隔离',
    badCred: '用户名或口令错误',
    badCaptcha: '验证码错误',
    loginFail: '登录失败：'
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
    perm: '权限管理',
    approvalflow: '审批流配置',
    board: '看板与留痕',
    feedback: '建议与反馈',
    settings: '系统设置'
  },

  // ---- 顶栏 ----
  top: {
    searchPlaceholder: '搜索…（试试 TLS1.1）',
    logout: '退出'
  },

  // ---- 仪表盘外壳（面包屑等）----
  dashboard: {
    crumb: '概览',
    crumbCurrent: '合规态势总览'
  },

  // ---- 各页面包屑（顶栏 group / current 两段，对齐原型 crumb 映射）----
  // 用法：crumb.<routeKey>.g（分组）/ .c（当前页）
  crumb: {
    dashboard: { g: '概览', c: '合规态势总览' },
    extaudit: { g: '业务', c: '外部审计' },
    todo: { g: '概览', c: '我的待办' },
    audit: { g: '业务', c: '内部审计' },
    risk: { g: '业务', c: '风险评估' },
    law: { g: '业务', c: '法规跟踪' },
    regaffairs: { g: '业务', c: '监管事项' },
    obligation: { g: '业务', c: '合规清单' },
    policy: { g: '业务', c: '制度体系' },
    ai: { g: '业务', c: 'AI 智能问答' },
    vendor: { g: '业务', c: '第三方供应商' },
    org: { g: '资产与组织', c: '组织与资产' },
    notify: { g: '系统管理', c: '通知中心' },
    aimodel: { g: '系统管理', c: '模型接入' },
    perm: { g: '系统管理', c: '权限管理' },
    approvalflow: { g: '系统管理', c: '审批流配置' },
    board: { g: '系统管理', c: '看板与留痕' },
    feedback: { g: '系统管理', c: '建议与反馈' },
    settings: { g: '系统管理', c: '系统设置' }
  },

  // ---- 全局浮动 AI 助手按钮 ----
  aiFab: {
    title: 'AI 助手'
  },
  aiPanel: {
    title: 'AI 合规助手',
    clear: '清空对话',
    intro: '基于本组织知识库的检索增强问答。可追问，我会结合上文回答；答案附引用来源，请以正式文件为准。',
    askFail: '提问失败：',
    fb: {
      ask: '这条回答有用吗？',
      thanksUp: '已记录，感谢反馈',
      thanksDown: '已记录，我们会改进',
      reasonPh: '哪里不对？（可选，帮助改进）',
      submit: '提交'
    }
  },

  // ---- 占位页（尚未复原的菜单项，点击进入此页保证不报错）----
  placeholder: {
    tag: '建设中',
    title: '{name}',
    desc: '该模块原型尚未复原，敬请期待。当前可正常导航、主题与语言切换均生效。'
  },

  // ---- 外部审计（M3-EXT，严格对齐驾驶舱版原型 #view-extaudit）----
  extaudit: {
    tag: '外部审计',
    title: '外部审计',
    register: '＋ 登记外审任务',
    // 子公司分段
    seg: { all: '集团', pay: '支付科技', consumer: '消费金融', tech: '数科' },
    // 三个 Tab
    tab: { tasks: '外审任务', findings: '外部审计发现', remed: '整改跟踪' },
    // KPI
    kpi: {
      active: '进行中外审',
      bodies: '被审计对象',
      openFindings: '外部发现(未闭环)',
      toRemed: '待整改',
      certPassed: '本年通过认证'
    },
    // 外审任务表
    tasks: {
      title: '外部审计任务',
      sub: '按组织隔离',
      th: {
        id: '编号',
        cert: '认证体系',
        body: '审计机构',
        owner: '责任单位',
        cycle: '周期',
        planStart: '计划开始',
        status: '状态'
      },
      // 状态标签
      status: {
        onsite: '现场审计中',
        pendingReply: '待回复发现',
        remediating: '整改中',
        awaitReg: '待监管回复',
        passed: '已通过'
      }
    },
    // 认证体系分布
    dist: { title: '按认证体系分布' },
    // 认证有效期临近
    expiry: {
      title: '认证有效期临近',
      pci: 'PCI DSS（支付科技）',
      iso: 'ISO 27001（集团）',
      mlps: '等保三级（支付科技）',
      pciLeft: '剩 28 天',
      isoLeft: '剩 73 天',
      mlpsLeft: '剩 120 天'
    },
    // 计划临近提醒（企微）
    remind: {
      title: '计划临近提醒（企微）',
      sub: '计划开始前自动提醒',
      leadDays: '提前天数（可配）',
      leadDaysV: '15 天 / 10 天',
      bot: '接收企微机器人',
      botV: '外审通知群机器人',
      task: 'EA-2026-07 等保测评',
      taskV: '计划 07-10 · 剩 14 天',
      status: '状态',
      statusV: '启用'
    },
    // 外部审计发现表
    findings: {
      title: '外部审计发现',
      sub: '来自外部机构',
      th: {
        id: '发现编号',
        source: '来源外审',
        issue: '问题',
        cert: '认证体系',
        sev: '严重度',
        owner: '责任单位',
        status: '整改状态'
      },
      issue: {
        chd: '持卡人数据传输未全程加密',
        access: '访问评审记录不完整',
        log: '备付金日志留存不足',
        baseline: '部分主机基线未达标',
        privacy: '隐私告知文案待完善'
      },
      status: { remediating: '整改中', toRemed: '待整改', verified: '已验证' }
    },
    // 发现按严重度（五级）
    sevDist: {
      title: '发现按严重度（五级）',
      vh: '极高',
      h: '高',
      m: '中',
      l: '低',
      vl: '极低'
    },
    // 整改任务表
    remTasks: {
      title: '外部发现整改任务（含企微通知状态）',
      th: {
        task: '任务',
        source: '来源发现',
        owner: '责任人',
        due: '截止',
        notify: '企微通知',
        status: '状态'
      },
      notify: { dueSoon: '已通知·期限临近', escalated: '已升级上级', notified: '已通知' },
      status: { remediating: '整改中', overdue: '逾期', pending: '待处理' }
    },
    // 对外闭环漏斗
    funnel: {
      title: '对外闭环漏斗',
      external: '外部发现',
      internalFix: '内部整改',
      internalVerified: '内部已验证',
      submitted: '已向外部机构提交',
      accepted: '外方受理',
      closed: '外方确认关闭'
    },
    // 对外回函与受理
    reply: {
      title: '对外回函与受理',
      th: { audit: '外审', report: '报告', submitted: '提交日', accepted: '外方受理', conclusion: '结论' },
      accepted: { underReview: '审核中', accepted: '已受理' },
      conclusion: { pending: '待确认', moreEvidence: '要求二次举证', closed: '确认关闭' }
    }
  },

  // ---- 监管事项（M11，严格对齐驾驶舱版原型 #view-regaffairs）----
  regaffairs: {
    tag: '监管事项',
    title: '监管事项',
    register: '＋ 登记监管事项',
    // 登记监管报送弹窗
    create: { title: '登记监管报送', item: '报送事项', itemPh: '如：支付业务统计报表', regulator: '监管机构', regulatorPh: '如：人民银行', deadline: '法定时限', org: '归属组织', orgPay: '支付科技', orgConsumer: '消费金融', confirm: '确认登记' },
    // 子公司分段
    seg: { all: '集团', pay: '支付科技', consumer: '消费金融' },
    // KPI 五卡
    kpi: {
      dueMonth: '本月待报送',
      dueMonthSub: '2 项法定时限临近',
      overdue: '逾期报送',
      openInquiry: '未结问询',
      penaltyOpen: '处罚整改未闭环',
      majorReport: '重大事件报送',
      majorReportSub: '本季'
    },
    // 五个 Tab
    tab: {
      calendar: '报送日历',
      plan: '年度合规计划',
      inquiry: '监管问询',
      penalty: '处罚与约谈',
      major: '重大事件报送'
    },
    // Tab1 · 报送日历
    calendar: {
      title: '监管报送日历',
      sub: '法定时限',
      th: { item: '报送事项', regulator: '监管机构', type: '类型', deadline: '法定时限', owner: '责任人', status: '状态', op: '操作' },
      // 报送事项名称
      itemPbocStat: '支付业务统计报表',
      itemAml: '反洗钱报告',
      itemPi: '个人信息保护合规报告',
      itemReserve: '备付金存管报表',
      // 监管机构
      regPboc: '人民银行',
      regAml: '反洗钱中心',
      regCac: '网信办',
      // 类型
      typeMonthly: '月报',
      typeQuarterly: '季报',
      typeAnnual: '年报',
      // 法定时限文案
      due0705: '07-05（剩 2 天）',
      // 状态
      stDrafting: '编制中',
      stToDraft: '待编制',
      stSubmitted: '已报送',
      // 责任人（avatar 取姓氏首字）
      ownerChen: '陈强',
      ownerLi: '李娜',
      ownerWang: '王芳',
      ownerZhang: '张伟'
    },
    // Tab1 右栏 · 报送达成
    achieve: {
      title: '报送达成（本年）',
      onTime: '按时报送率',
      monthly: '月报',
      quarterly: '季报'
    },
    // Tab1 右栏 · 临近报送
    upcoming: {
      title: '临近报送',
      d2: '剩 2 天',
      d7: '剩 7 天',
      d12: '剩 12 天'
    },
    // Tab2 · 监管问询
    inquiry: {
      title: '监管问询',
      add: '＋ 登记问询',
      th: { id: '编号', regulator: '监管机构', subject: '事项', received: '收文日', replyDue: '答复期限', replyLog: '操作', status: '状态' },
      regPboc: '人民银行',
      regCac: '网信办',
      regNafr: '金融监管总局',
      subjLargeTxn: '大额交易监测机制问询',
      subjPiExport: '个人信息出境情况说明',
      subjOutsource: '外包风险管理情况',
      stReplyDrafting: '答复编制中',
      stReplied: '已答复·留痕',
      stAwaitFeedback: '待监管反馈',
      stClosed: '已办结'
    },
    // Tab3 · 处罚与约谈台账
    penalty: {
      title: '处罚与约谈台账',
      add: '＋ 登记',
      th: { id: '编号', type: '类型', regulator: '监管机构', reason: '事由', date: '日期', remediation: '操作', replyStatus: '回函状态' },
      typeTalk: '约谈',
      typePenalty: '行政处罚',
      regPboc: '人民银行',
      regLocalPboc: '属地央行',
      reasonKyc: '商户实名制落实不到位',
      reasonReserve: '备付金管理违规',
      remed30d: '30 日内整改并报告',
      remedFine: '限期整改+罚款',
      stRemediating: '整改中·待回函',
      stRepliedClosed: '已回函·监管办结'
    },
    // Tab4 · 重大事件报送
    major: {
      info: '重大风险/安全事件须按监管时限上报（如人民银行重大事件报告制度）；此处管理报送的时限、版本与回执留痕。',
      emptyTitle: '本季无重大事件报送',
      emptyDesc: '发生重大事件时，从此处发起监管报送并跟踪回执；与安全事件、整改、证据留痕联动。',
      thSeverity: '严重度',
      thOccurred: '发生时间',
      thReported: '上报时间'
    },
    // Tab5 · 年度合规计划
    plan: {
      kpi: { total: '年度合规事项', done: '已完成', doing: '进行中', overdue: '逾期' },
      title: '2026 年度合规计划',
      add: '＋ 新增事项',
      th: { item: '事项', category: '类别', dept: '责任部门', planDone: '计划完成', progress: '进度', status: '状态' },
      itemMlps: '等保三级测评（支付科技）',
      itemPiTrain: '全员个人信息保护培训',
      itemAmlInspect: '反洗钱专项检查',
      itemDataSelf: '数据安全合规自评',
      catAssess: '测评',
      catTrain: '培训',
      catInspect: '检查',
      catSelf: '自评',
      deptInfosec: '信息安全部',
      deptCompliance: '合规部',
      stDoing: '进行中',
      stDone: '已完成',
      stOverdue: '逾期',
      // ---- 真实后端接通（M11 CompliancePlan，替换原占位）----
      list: '年度计划',
      items: '计划事项',
      empty: '暂无年度计划',
      itemEmpty: '该计划暂无事项',
      selectHint: '← 选择左侧一个年度计划查看其事项',
      addItem: '＋ 新增事项',
      th: { year: '年度', title: '名称', owner: '责任人', status: '状态', op: '操作' },
      ith: { matter: '事项', dept: '责任部门', due: '计划完成', status: '状态' },
      status: { DRAFT: '草稿', ACTIVE: '执行中', CLOSED: '已关闭' },
      istatus: { PENDING: '待办', IN_PROGRESS: '进行中', DONE: '已完成' },
      op: { activate: '启用', close: '关闭' },
      create: { btn: '＋ 登记计划' }
    },
    // ---- 后端真实联调相关（DM-5）----
    // 空列表行（后端真实返回空）
    emptyRow: '暂无数据（后端真实）',
    // 占位提示（后端无对应字段/能力）
    dash: '—',
    // 报送日历状态枚举 RegFilingStatus → 中文
    filingStatus: { TO_DRAFT: '待编制', DRAFTING: '编制中', PENDING_REVIEW: '待审批', SUBMITTED: '已报送', CLOSED: '已办结' },
    // 报送审批两步化操作
    filingOp: { prepare: '准备', submit: '提交评审', approve: '审批通过', reject: '驳回', close: '关闭' },
    // 监管问询状态枚举 RegInquiryStatus → 中文
    inquiryStatus: { DRAFTING: '答复编制中', REPLIED: '已答复', AWAIT_FEEDBACK: '待监管反馈', CLOSED: '已办结' },
    // 处罚约谈状态枚举 RegPenaltyStatus → 中文
    penaltyStatus: { OPEN: '待整改', RECTIFYING: '整改中', CLOSED: '已办结' },
    // 重大事件状态枚举 MajorIncidentStatus → 中文
    incidentStatus: { DRAFT: '草稿', REPORTED: '已上报', CLOSED: '已办结' },
    // 重大事件严重度五级 MajorIncidentSeverity → 中文
    severity: { VERY_LOW: '极低', LOW: '低', MID: '中', HIGH: '高', VERY_HIGH: '极高' }
  },

  // ---- 风险评估（M2，严格对齐驾驶舱版原型 #view-risk 及其下钻视图）----
  risk: {
    tag: '风险评估',
    title: '风险评估闭环',
    newAssess: '＋ 发起评估',
    // 发起评估弹窗
    create: { obj: '评估对象', objPh: '如：核心支付网关等保自评', assessor: '评估人', period: '评估周期', org: '归属组织', orgPay: '支付科技', orgConsumer: '消费金融', confirm: '确认发起' },
    // 风险发现 · 关闭门控（CR-002 红线）
    gate: {
      title: '风险发现 · 关闭门控',
      badge: '关闭门控 · 高残余须管理层接受',
      scaffoldNote: '上方综合指数 / 等级分布 / 风险点清单为原型视觉示意；下方「风险发现 · 关闭门控」为真实后端数据与 CR-002 红线。',
      th: { finding: '风险发现', inherent: '固有', residual: '残余', acceptance: '风险接受', status: '状态', ops: '操作' },
      missing: '缺接受·门控',
      gatedTip: '残余高/极高风险须先登记风险接受方可关闭（CR-002 残余风险关闭门控）',
      requestAccept: '申请风险接受',
      approveAccept: '审批通过',
      rejectAccept: '驳回',
      close: '关闭',
      verify: '验证',
      verified: '已验证',
      empty: '该评估暂无风险发现',
      fstatus: { OPEN: '待处置', IN_TREATMENT: '处置中', DONE: '已关闭', VERIFIED: '已验证' },
      acceptTitle: '申请风险接受',
      reason: '接受理由',
      reasonPh: '如：业务必要性 + 补偿性控制，待管理层审批',
      acceptConfirm: '提交申请'
    },
    // 顶部 Tab
    tab: {
      tasks: '评估任务',
      register: '风险登记册',
      templates: '模板库',
      controls: '统一控件库',
      kri: 'KRI 监控',
      atv: 'A-T-V 建模'
    },
    // Tab1 · 评估任务
    kpi: {
      active: '进行中',
      pending: '待审批',
      highRisk: '高风险评估',
      overdue: '逾期',
      doneQuarter: '本季完成'
    },
    tasks: {
      title: '评估任务',
      th: { id: '编号', obj: '对象', tpl: '体系模板', prog: '进度', risk: '风险值', due: '截止', status: '状态' },
      obj: { gateway: '核心支付网关', settle: '商户结算系统', warehouse: '数据仓库平台' },
      status: { filling: '填写中', pending: '待审批', live: '已生效' }
    },
    // 评估状态（对齐后端 AssessmentStatus）
    assessStatus: { DRAFT: '草稿', IN_PROGRESS: '进行中', PENDING_REVIEW: '待复核', COMPLETED: '已完成', CANCELLED: '已作废' },
    // 风险等级分布（五级）
    levelDist: {
      title: '风险等级分布',
      sub: '五级',
      vh: '极高',
      h: '高',
      m: '中',
      l: '低',
      vl: '极低'
    },
    // 评估进度漏斗
    funnel: {
      title: '评估进度漏斗',
      started: '已发起',
      filling: '填写中',
      pending: '待审批',
      live: '已生效'
    },
    // Tab2 · 模板库
    templates: {
      newTpl: '新建体系模板',
      tstatus: { DRAFT: '草稿', PUBLISHED: '已发布', RETIRED: '已停用' },
      fw: { MLPS: '等保 2.0', ISO27001: 'ISO 27001', PCI_DSS: 'PCI DSS', PBOC: 'PBOC 支付合规' },
      f: { framework: '合规框架', desc: '模板说明' },
      ctrlPoints: '控制点',
      ctrlItems: '控制项',
      questions: '题',
      cards: {
        mlps: { name: '等保三级测评', desc: '安全通用+扩展，控制点编号组织', meta: '211 控制点' },
        iso: { name: 'ISO 27001', desc: '资产-威胁-脆弱性四要素', meta: '114 控制项' },
        pci: { name: 'PCI DSS', desc: '持卡人数据环境 12 类', meta: '78 控制点' },
        pboc: { name: 'PBOC 支付合规', desc: '非银行支付机构监管映射', meta: '96 控制点' },
        iso27701: { name: 'ISO 27701', desc: '隐私信息管理 PIMS', meta: '49 控制项' },
        vendor: { name: '供应商评估', desc: '合同条款+履约，定量叠加定性', meta: '54 题' },
        iso9001: { name: 'ISO 9001', desc: '质量管理过程方法', meta: '61 控制项' }
      }
    },
    // Tab3 · 统一控件库
    controls: {
      title: '统一控件库',
      sub: '具体控制点颗粒度',
      newCtrl: '＋ 定义控件',
      noMap: '未映射',
      empty: '暂无控件',
      th: { id: '编号', ctrl: '控制点', systems: '覆盖体系', reuse: '复用', result: '结果' },
      cstatus: { ACTIVE: '启用', RETIRED: '已停用' },
      f: { domain: '控制域', domainPh: '如：访问控制 / 加密 / 日志审计' },
      ctrl: { priv: '特权账号定期审查', tls: '数据传输加密 TLS1.2+', acl: '访问控制最小权限' },
      result: { ok: '符合', partial: '部分不符' },
      reuseTop: {
        title: '复用 Top',
        acl: '访问控制最小权限',
        priv: '特权账号审查',
        log: '日志留存'
      }
    },
    // Tab4 · KRI 监控
    kri: {
      kpi: { metrics: '监控指标', breach: '超阈值预警', critical: '严重(超阈值)', warning: '预警', normal: '正常', sources: '采集源', sourcesSub: 'SIEM/日志/漏洞', collect: '采集状态', collectV: '正常' },
      title: 'KRI 指标与阈值',
      config: '＋ 配置指标',
      newKri: '＋ 定义指标',
      empty: '暂无 KRI 指标',
      th: { metric: '指标', owner: '责任人', source: '采集源', current: '当前', threshold: '预警/严重阈值', status: '状态' },
      cstatus: { CRITICAL: '严重', WARNING: '预警', NORMAL: '正常', UNKNOWN: '未测量' },
      dir: { UPPER_BAD: '越高越坏', LOWER_BAD: '越低越坏' },
      f: { unit: '计量单位', direction: '阈值方向', warn: '预警阈值', crit: '严重阈值' },
      rows: {
        vuln: { metric: '核心系统漏洞修复时效', source: '漏洞管理', current: '23.4 天', threshold: '≤15 天', status: '超阈值' },
        priv: { metric: '特权账号未审查数', source: 'SIEM', current: '7', threshold: '=0', status: '紧急' },
        log: { metric: '日志留存天数', source: '日志平台', current: '162 天', threshold: '≥180 天', status: '关注' }
      },
      st: { over: '超阈值', urgent: '紧急', watch: '关注' }
    },
    // 三参考库「新建」弹窗共用字段
    ref: { code: '编号', name: '名称', owner: '责任人', org: '归属组织' },
    // 下钻 · 评估报告（固有/残余风险与管理层接受、综合风险指数下钻）
    report: {
      back: '← 返回评估任务',
      title: '商户结算系统 · 风险评估报告',
      pending: '待审批',
      exportPdf: '导出 PDF',
      sign: '复核签批',
      kpi: { riskVal: '风险值', high: '高风险', points: '风险点', highPoints: '高风险点', toRemed: '已转整改' },
      list: {
        title: '风险点清单',
        th: { ctrl: '控制点', concl: '结论', level: '等级', advice: '建议整改' },
        rows: {
          acl: { ctrl: '结算接口访问控制', concl: '存在共享账号', advice: '启用个人账号+审计' },
          tls: { ctrl: '数据传输加密', concl: '部分 TLS1.1', advice: '升级 TLS1.2+' },
          log: { ctrl: '日志留存', concl: '满足要求', advice: '—' }
        }
      },
      donut: { title: '等级分布' },
      residual: {
        title: '风险处置与残余风险',
        th: { point: '风险点', inherent: '固有风险', decision: '处置决策', measure: '处置措施', residual: '残余风险', accept: '责任人接受' },
        decision: { mitigate: '降低', accept: '接受' },
        accept: { pending: '待签认', accepted: '管理层已接受' },
        rows: {
          shared: { point: '结算接口共享账号', measure: '启用个人账号+审计' },
          tls: { point: '部分 TLS1.1', measure: 'Q3 升级 TLS1.2+' },
          backup: { point: '老旧备份介质', measure: '限期至下次评估复核' }
        }
      }
    }
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
    subtitle: '全集团 · 数据截至 {t} · 准实时',
    editLayout: '编辑布局',
    addWidget: '添加组件',
    overdue: '逾期{n}',
    seg: { all: '全集团', pay: '支付科技', consumer: '消费金融', tech: '数科' },
    due: { pending: '待处理 {v}', overdue: '逾期 {v}' },

    scaffoldNote: '本页 KPI 卡 / 热力矩阵 / 整改完成率 / KRI 监控 / 待我审批 / 事件流均为真实后端数据（按可见组织)；体系达成度待控制点覆盖率能力交付后接入。',
    // KPI 指标卡（l=标题, s=辅助说明）——接 /api/dashboard/summary 真实计数
    kpi: {
      openRisk: { l: '未关闭风险发现', s: '风险评估·处置中' },
      gated: { l: '被门控发现', s: '残余高·未放行(CR-002)' },
      kriWarn: { l: 'KRI 预警', s: '触及预警阈值' },
      kriCrit: { l: 'KRI 严重', s: '达严重阈值·红线' },
      openAudit: { l: '未关闭审计发现', s: '内/外审待闭环' },
      pendingFiling: { l: '待报送', s: '未报送/复核中' },
      effPolicy: { l: '现行有效制度', s: '已发布生效' },
      pendingSod: { l: '待审批 SoD 豁免', s: '职责分离例外' }
    },

    // 热力矩阵
    heat: {
      title: '组织 × 风险域 · 热力矩阵',
      sub: '未决事项计数（真值）',
      domain: {
        infosec: '信息安全',
        data: '数据合规',
        continuity: '业务连续',
        thirdparty: '第三方',
        reg: '监管合规',
        control: '内控',
        risk: '风险敞口',
        vendor: '第三方',
        audit: '审计发现',
        remed: '待整改'
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
  },

  // ========================================================
  // 组织与资产页（OrgAssetView · M6）
  // 对应原型 #view-org：组织架构 / 资产台账 / 个人信息处理活动(ROPA) 三 Tab
  // ========================================================
  orgasset: {
    tag: '资产与组织',
    title: '组织架构与资产台账',
    adSync: 'AD 同步',
    register: '＋ 登记资产',
    // 三个 Tab
    tab: {
      org: '组织架构',
      asset: '资产台账',
      ropa: '个人信息处理活动(ROPA)'
    },
    // ---- Tab1 组织架构 ----
    tree: {
      title: '组织树（手动维护）',
      manualSync: '手动同步',
      // 组织节点
      hq: '集团总部',
      payCo: '支付科技子公司',
      payDepts: '研发部 / 运维部 / 风控部',
      merchantCo: '商户服务子公司',
      dataCo: '数据科技子公司',
      overseasCo: '海外业务子公司',
      // 人数后缀
      people128: '128 人',
      people312: '312 人',
      people256: '256 人',
      people189: '189 人',
      people98: '98 人'
    },
    // AD 同步态势侧卡
    adStatus: {
      title: 'AD 同步态势',
      lastSync: '最近同步',
      lastSyncVal: '今天 06:00',
      users: '同步用户',
      disabled: '停用（离职）',
      deprovision: '离职回收时效',
      deprovisionVal: '≤同步周期',
      status: '状态',
      statusVal: '正常'
    },
    // ---- Tab2 资产台账 ----
    kpi: {
      total: '资产总数',
      systems: '信息系统',
      highCrit: '高重要性',
      coverage: '评估覆盖率'
    },
    asset: {
      title: '资产台账',
      sub: '含数据/合规属性',
      th: {
        id: '编号',
        name: '名称',
        type: '类型',
        dataClass: '数据分级',
        pi: '个人信息',
        crossBorder: '跨境',
        mlps: '等保定级',
        chd: '持卡人数据',
        criticality: '重要性'
      },
      // 资产名称
      coreGw: '核心支付网关',
      merchantSettle: '商户结算系统',
      crossClearing: '跨境清算接口',
      dataWarehouse: '数据仓库平台',
      yunqing: '云擎科技',
      // 类型 pill
      typeSystem: '信息系统',
      typeVendor: '供应商',
      // 分级 / 标识
      sensitive: '敏感',
      internal: '内部',
      yes: '是',
      no: '否',
      l3: '三级',
      l2: '二级',
      high: '高',
      mid: '中'
    },
    // 资产类型分布 bars
    dist: {
      title: '资产类型分布',
      system: '信息系统',
      process: '业务流程',
      data: '数据资产',
      vendor: '供应商'
    },
    // ---- Tab3 ROPA ----
    ropa: {
      title: '个人信息处理活动记录（ROPA）',
      sub: 'PIPL 法定台账',
      add: '＋ 新增活动',
      th: {
        activity: '处理活动',
        purpose: '处理目的',
        piType: '个人信息类型',
        volume: '数据量级',
        sensitive: '敏感',
        export: '出境',
        retention: '保留期',
        owner: '责任人'
      },
      // 处理活动
      merchantSettle: '商户结算',
      crossClearing: '跨境清算',
      riskModel: '风控建模',
      service: '客服回访',
      // 目的
      clearing: '清算结算',
      crossPay: '跨境支付',
      antiFraud: '反欺诈',
      quality: '服务质量',
      // 个人信息类型
      idCard: '身份/银行卡号',
      idTxn: '身份/交易明细',
      deviceBehavior: '设备/行为',
      phoneCall: '手机号/通话',
      // 量级
      vMillion: '百万级',
      vHundredK: '十万级',
      vTenMillion: '千万级',
      // 保留期
      y10: '10 年',
      y5: '5 年',
      y2: '2 年',
      // 是否
      yes: '是',
      no: '否',
      // 责任人
      ownerLi: '李娜',
      ownerLiu: '刘洋',
      ownerWang: '王芳',
      ownerChen: '陈强'
    }
  },

  // ---- 看板与留痕（BoardView）----
  board: {
    tag: 'AUDIT TRAIL · 防篡改',
    title: '看板与留痕',
    org: { group: '集团', pay: '支付科技', consumer: '消费金融' },
    verify: {
      title: '哈希链完整性校验',
      badge: '防篡改',
      org: '组织链',
      run: '校验',
      valid: '链完整 · 已校验 {n} 条',
      broken: '断链/篡改 · 异常于序号 {seq}'
    },
    trail: {
      title: '操作留痕',
      entityPh: '对象（如 POLICY:5）',
      actionPh: '动作（如 POLICY_APPROVE）',
      actorPh: '操作人',
      query: '查询',
      empty: '暂无留痕（或调整过滤条件）',
      th: { seq: '序号', time: '时间', action: '动作', actor: '操作人', entity: '对象', detail: '详情', hash: '入链哈希' }
    }
  },

  // ---- 我的待办（MyTasksView）----
  todo: {
    tag: 'WORKBENCH · 我的待办',
    title: '我的待办',
    refresh: '刷新',
    listTitle: '组织范围待办',
    empty: '暂无待办',
    myApprovals: '我的审批（按登录人）',
    myEmpty: '暂无分给我的审批',
    th: { type: '类型', matter: '事项', due: '期限', status: '状态' },
    ath: { biz: '业务', node: '审批节点', role: '角色组', time: '到达时间' },
    type: { REMEDIATION: '整改工单', COMPLIANCE_ITEM: '合规计划项', REG_FILING: '监管报送' },
    grp: {
      fill: '待填写', fillSub: '进行中评估', fillEmpty: '无待填写评估', draft: '草稿', filling: '填写中',
      sign: '待签署', signSub: '生效制度签署确认', signEmpty: '无待签署制度',
      remed: '待整改', remedSub: '逾期红 · 7日内琥珀', remedEmpty: '无待整改任务', remedDefault: '整改单'
    },
    due: { none: '无期限', overdue: '逾期 {n} 天', today: '今日到期', left: '剩 {n} 天' }
  },

  // ---- 通知中心（NotifyView）----
  notify: {
    tag: 'WORKBENCH · 通知中心',
    title: '通知中心',
    refresh: '刷新',
    listTitle: '调度提醒',
    empty: '暂无通知',
    th: { time: '时间', event: '事件', object: '对象', threshold: '触发阈值', receipt: '回执' },
    ack: '确认收到',
    mergeTip: '同对象同事件多次提醒已合并降噪',
    digest: {
      tab: '定期简报', sub: '近 {d} 天提醒聚合 · 未回执数',
      d7: '近 7 天', d30: '近 30 天', d90: '近 90 天',
      event: '事件类型', total: '提醒次数', unread: '未回执', empty: '该周期内无提醒。',
      aiHint: '管理层文字版合规简报：到「AI 智能问答」页点「生成管理层简报」（基于全量真实统计，AI 初稿须人工复核）。'
    }
  },

  // ---- 法规跟踪（RegulationView）----
  reg: {
    tag: 'REGULATION · 法规跟踪',
    title: '法规跟踪',
    lib: '法规库',
    changes: '变更动态',
    empty: '暂无法规',
    changeEmpty: '该法规暂无变更动态',
    selectHint: '← 选择左侧一条法规查看其变更动态',
    org: { pay: '支付科技', consumer: '消费金融' },
    th: { code: '编号', title: '标题', issuer: '发布机构', status: '状态' },
    status: { TRACKING: '跟踪中', EFFECTIVE: '现行有效', SUPERSEDED: '已被取代', ABOLISHED: '已废止' },
    cth: { type: '类型', date: '日期', desc: '说明', impact: '影响评估', op: '操作' },
    changeType: { ENACTED: '新订', AMENDED: '修订', ABOLISHED: '废止' },
    impact: { PENDING: '待评估', ASSESSED: '已评估' },
    create: { btn: '＋ 登记法规', category: '分类', org: '归属组织', ok: '确认登记' },
    change: { btn: '＋ 登记变更', ok: '确认登记' },
    assess: { btn: '影响评估', title: '法规变更·影响评估', scope: '受影响范围', scopePh: '如：涉及 KYC 制度、客户身份识别控制项', note: '处置说明', ok: '提交评估' }
  },

  // ---- 第三方供应商（VendorView）----
  vendor: {
    tag: 'VENDOR · 第三方供应商',
    title: '第三方供应商',
    list: '供应商',
    assessHist: '评估历史',
    empty: '暂无供应商',
    assessEmpty: '该供应商暂无评估',
    selectHint: '← 选择左侧一个供应商查看评估历史',
    unassessed: '未评估',
    gateTip: '未完成风险评估不得启用（准入门控）',
    org: { pay: '支付科技', consumer: '消费金融' },
    th: { code: '编号', name: '名称', risk: '风险', status: '状态', op: '操作' },
    ath: { risk: '风险等级', score: '得分', assessor: '评估人', concl: '结论' },
    status: { ONBOARDING: '准入中', ACTIVE: '合作中', SUSPENDED: '已暂停', TERMINATED: '已终止' },
    level: { VERY_HIGH: '极高', HIGH: '高', MID: '中', LOW: '低', VERY_LOW: '极低' },
    op: { assess: '评估', activate: '启用', suspend: '暂停', reactivate: '恢复' },
    create: { btn: '＋ 登记供应商', category: '服务类别', criticality: '重要性', org: '归属组织', ok: '确认登记' },
    assess: { title: '供应商风险评估', risk: '风险等级', score: '得分(0–100)', concl: '评估结论', ok: '提交评估' }
  },

  // ---- 合规清单（ObligationView）----
  obl: {
    tag: 'OBLIGATION · 合规清单',
    title: '合规清单',
    list: '合规义务',
    empty: '暂无义务',
    org: { pay: '支付科技', consumer: '消费金融' },
    th: { code: '编号', title: '义务', source: '来源', dept: '责任部门', due: '期限', status: '状态', op: '操作' },
    status: { PENDING: '待落实', IN_PROGRESS: '落实中', FULFILLED: '已落实', NON_COMPLIANT: '不合规' },
    op: { start: '开始落实', fulfill: '标记已落实', nc: '标记不合规' },
    create: { btn: '＋ 登记义务', source: '来源', sourcePh: '如：PBOC-AML-2026', org: '归属组织', ok: '确认登记' },
    fulfill: { title: '标记已落实', evidence: '落实证据', evidencePh: '如：已上线监测系统、月报存档 #2026-06', ok: '确认落实' }
  },

  // ---- 建议与反馈（FeedbackView）----
  fb: {
    tag: 'FEEDBACK · 建议与反馈',
    title: '建议与反馈',
    list: '反馈',
    empty: '暂无反馈',
    org: { pay: '支付科技', consumer: '消费金融' },
    th: { type: '类型', title: '标题', submitter: '提交人', handler: '处理人', status: '状态', op: '操作' },
    type: { SUGGESTION: '建议', COMPLAINT: '投诉', BUG: '缺陷', QUESTION: '咨询' },
    status: { SUBMITTED: '待受理', IN_PROGRESS: '处理中', RESOLVED: '已办结', CLOSED: '已关闭', REJECTED: '已驳回' },
    op: { triage: '受理', resolve: '办结', close: '关闭', reject: '驳回' },
    create: { btn: '＋ 提交反馈', content: '内容', org: '归属组织', ok: '确认提交' },
    triage: { handler: '处理人', handlerPh: '指派处理人，如：合规部-张' },
    resolve: { title: '办结反馈', resolution: '处置结果', resolutionPh: '说明处置结论与措施' },
    view: { list: '列表', board: '看板' },
    ob: {
      col: '出站', submit: '出站回复', approve: '出站批准', reject: '出站驳回',
      PENDING_APPROVAL: '待审批', APPROVED: '已批准', REJECTED: '已驳回',
      title: '出站回复（须审批）', hint: '回复稿经审批通过后方可对外发送。',
      field: '对外回复稿', ph: '对外回复内容…', ok: '提交审批'
    }
  },

  // ---- 系统设置（SettingsView）----
  set: {
    tag: 'SETTINGS · 系统设置',
    title: '系统设置',
    list: '配置项',
    empty: '暂无配置',
    editableYes: '可改',
    locked: '锁定',
    lockedDash: '—',
    lockTip: '系统锁定项不可修改',
    org: { pay: '支付科技', consumer: '消费金融' },
    th: { key: '键', category: '分组', type: '类型', value: '取值', editable: '可配置', op: '操作' },
    op: { edit: '修改' },
    create: { btn: '＋ 定义配置', editable: '允许租户修改', org: '归属组织', ok: '确认定义' },
    edit: { title: '修改取值', value: '新取值', ok: '保存' }
  },

  // ---- 制度发布（PolicyView · M1）----
  policy: {
    tag: 'POLICY · 制度发布',
    title: '制度发布',
    list: '制度',
    empty: '暂无制度',
    deprecated: '已废止',
    org: { pay: '支付科技', consumer: '消费金融' },
    th: { code: '编号', title: '标题', version: '版本', status: '状态', op: '操作' },
    status: { DRAFT: '草稿', REVIEW: '评审中', EFFECTIVE: '现行有效', DEPRECATED: '已废止' },
    op: { submit: '提交评审', approve: '审批通过', reject: '驳回', signoff: '签署', archive: '废止' },
    create: { btn: '＋ 新建制度', content: '正文', org: '归属组织', ok: '确认新建' },
    reject: { title: '驳回制度评审', reason: '驳回原因', reasonPh: '说明驳回理由' }
  },

  // ---- 权限与职责分离（PermissionView · M8）----
  perm: {
    tag: 'PERMISSION · 权限与 SoD',
    title: '权限与职责分离',
    sodRules: 'SoD 职责分离规则',
    sodRulesSub: '系统级·互斥角色对',
    rth: { pair: '互斥角色对', mode: '强制模式', desc: '说明' },
    mode: { BLOCK: '硬阻断', DETECT: '检测型' },
    ruleDesc: { 1: '发起人与审批人不得由同一用户在同一组织兼任（高敏·授权即阻断）', 2: '风险责任人与审计员职责分离（检测型·登记冲突待例外审批）' },
    grantTitle: '用户角色授权',
    user: '用户',
    query: '查询授权',
    grantRole: '授予角色',
    active: '在用',
    revoked: '已收回',
    noGrant: '该用户暂无授权',
    gth: { role: '角色', by: '授予人', state: '状态', op: '操作' },
    op: { grant: '授权', revoke: '收回', requestEx: '申请例外', approveEx: '审批通过', rejectEx: '驳回' },
    exTitle: 'SoD 例外',
    exSub: '申请 → 审批两步',
    exRule: '冲突规则',
    exReason: '申请理由',
    exReasonPh: '说明业务必要性与补偿控制',
    exEmpty: '本会期暂无例外申请',
    exStatus: { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' },
    exth: { rule: '规则', user: '用户', status: '状态', op: '操作' },
    // 访问复核 UAR
    uar: {
      title: '访问复核（UAR）',
      sub: '周期性权限再认证',
      period: '复核周期',
      reviewer: '复核人',
      reviewerPh: '如：合规部-李',
      org: '归属组织',
      create: '新建复核',
      batches: '复核批次',
      items: '审阅项',
      empty: '本会期暂无复核批次',
      itemEmpty: '该批次暂无审阅项（开始后快照有效授权）',
      selectHint: '← 选择左侧一个批次查看审阅项',
      grantRef: '授权 #{id}',
      th: { period: '周期', status: '状态', op: '操作' },
      ith: { grant: '授权', decision: '决定', op: '操作' },
      status: { OPEN: '待开始', IN_REVIEW: '复核中', COMPLETED: '已完成' },
      decision: { PENDING: '待定', KEEP: '保留', REVOKE: '收回' },
      op: { start: '开始', complete: '完成', keep: '保留', revoke: '收回' }
    }
  },

  // ---- AI 智能问答（AiAssistantView · RAG）----
  ai: {
    tag: 'AI · 智能问答',
    title: 'AI 智能问答',
    you: '你',
    placeholder: '就制度、法规、合规义务向知识库提问。回答仅依据已录入的知识，并标注引用来源。',
    askPh: '输入问题，回车或点「提问」…',
    ask: '提问',
    asking: '检索中…',
    thinking: '检索并生成中…',
    citations: '引用来源',
    mode: { local: '本地离线模式' },
    kb: '知识库',
    kbEmpty: '暂无知识，先录入制度/法规',
    dth: { title: '标题', type: '类型', chunks: '切块', status: '状态' },
    stype: { POLICY: '制度', REGULATION: '法规', OBLIGATION: '义务', MANUAL: '手工' },
    dstatus: { PENDING: '待索引', INDEXED: '已索引' },
    org: { pay: '支付科技', consumer: '消费金融' },
    ingest: { btn: '＋ 录入知识', ref: '来源引用', content: '正文', contentPh: '粘贴制度/法规正文，将自动切块并向量化', org: '归属组织', ok: '录入并索引' },
    ds: {
      title: '数据源覆盖',
      d1: 'M1 制度 / M4 法规（知识库 {n} 篇，语义检索）',
      d2: 'M2 风险 / M3 审计 / M11 监管（实时统计，材料生成用）',
      d3: '权限范围：仅你可见组织的数据（RLS 裁剪）'
    },
    gen: {
      title: '生成报送 / 汇报材料', sub: '基于当前真实合规统计 · AI 初稿须人工复核后使用',
      filing: '生成监管报送稿', brief: '生成管理层简报', busy: '生成中…',
      briefLabel: '管理层简报', filingLabel: '监管报送稿', asOf: '数据截至 {t} · 提供方 {p}',
      warn: '⚠ AI 生成初稿，须经人工复核、补充与签批后方可使用；数据以生成时点为准。',
      hint: '点右上按钮，基于当前可见组织的真实统计生成材料初稿。',
      fail: '生成失败：'
    },
    chunks: {
      title: '切块明细', chars: '{n} 字', loading: '加载中…',
      empty: '该文档暂无切块（可能尚未索引完成）。', rowTip: '点击查看切块',
      delTip: '删除文档及切块', delConfirm: '确认删除知识源「{t}」及其全部切块？'
    }
  },

  // ---- 模型接入（ModelAccessView）----
  aimodel: {
    tag: '模型接入',
    title: '模型接入',
    refresh: '刷新状态',
    current: '当前 AI 配置',
    llm: '生成模型（LLM）',
    embedding: '嵌入模型',
    network: '数据出境',
    local: '本地离线 · 未接大模型',
    embLocal: '本地确定性嵌入',
    noEgress: '不出境（全本地）',
    egress: '问答上下文发送外部模型服务商',
    localNote: '当前为本地离线模式：向量检索 + 离线摘要，全程不出网、不需密钥，数据不出境。仅返回检索摘要，不生成式作答。',
    claudeNote: '当前接入通用大模型：问答时检索片段与问题会发送至所配置的外部模型服务商。请确保符合数据出境与合规要求（PIPL/等保）。',
    howto: '切换指引',
    deploySide: '部署侧配置',
    howtoLead: '提供方与密钥由部署环境变量控制（应用重启生效），不在本界面录入：',
    provClaude: '通用大模型（Anthropic 协议）',
    provOpenai: '通用大模型（OpenAI 兼容协议，覆盖主流公有云与本地私有化推理服务）',
    keyDeploy: '（部署时注入，界面不展示）',
    keyWarn: '出于安全，API 密钥仅经部署环境变量注入，平台界面不提供密钥录入框、也不回显密钥。',
    wl: {
      title: '模型白名单', sub: '启用条目存在时强制管控',
      empty: '无白名单条目 = 未启用管控（任意模型可保存）。',
      add: '加入白名单', modelPh: '模型 id，如 qwen-plus', notePh: '备注（可选）'
    },
    pt: {
      title: '提示词模板', sub: '系统提示词集中管理',
      add: '新建模板', namePh: '模板名，如 条款级变更摘要',
      edit: '编辑', collapse: '收起', save: '保存正文'
    },
    gov: { enable: '启用', disable: '停用', del: '删', delConfirm: '确认删除「{t}」？' }
  },

  // ---- 审批流配置（ApprovalFlowDesignerView · Vue Flow 画布）----
  flow: {
    tag: 'APPROVAL FLOW · 审批流配置',
    title: '审批流配置',
    pickFlow: '— 选择流程 —',
    new: '＋ 新建',
    palette: '节点面板',
    props: '属性',
    save: '保存',
    validate: '校验',
    publish: '发布生效',
    namePh: '流程名称',
    selectHint: '点击画布上的节点或连线以编辑属性',
    connectHint: '从节点右侧圆点拖到下一节点左侧圆点即可连线；点连线设条件。',
    condHint: '条件用 JUEL，如 amount ge 100；留空为默认分支。',
    delNode: '删除节点',
    delEdge: '删除连线',
    edge: '连线',
    biz: { POLICY_PUBLISH: '制度发布', RISK_ACCEPT: '风险接受', SOD_EXCEPTION: 'SoD 例外', REG_FILING: '监管报送' },
    status: { DRAFT: '草稿', ACTIVE: '生效', RETIRED: '停用' },
    node: { START: '开始', APPROVAL: '审批节点', CONDITION: '条件', PARALLEL_SPLIT: '并行分叉', PARALLEL_JOIN: '并行合流', END: '结束' },
    at: { ROLE: '按角色', USER: '指定人', GROUP: '审批组' },
    mode: { ANY: '或签（任一/任 N 通过）', ALL: '会签（全部通过）' },
    oc: { APPROVED: '通过', REJECTED: '驳回' },
    f: {
      name: '节点名称', approverType: '审批人来源', approvers: '审批人', approversPh: '角色码/用户，逗号分隔，如 CHECKER,RISK_OWNER',
      mode: '完成逻辑', required: '或签通过人数', timeout: '超时(小时)', timeoutPh: '0=不启用',
      escType: '升级目标类型', escRef: '升级目标', outcome: '结束结论', condition: '条件表达式', conditionPh: '如 amount ge 100（留空=默认）'
    }
  },

  // ---- 权限配置（RbacConfigView · 增强③ R5）----
  rbac: {
    tag: 'RBAC · 权限配置',
    title: '权限配置',
    roles: '角色',
    matrix: '权限矩阵',
    newRole: '＋ 新建角色',
    create: '确认新建',
    save: '保存矩阵',
    super: '超管',
    superNote: '超级管理员对全部菜单/按钮均为读写，无需逐项配置。',
    selectHint: '← 选择左侧一个角色配置其菜单/按钮权限',
    resource: '资源（菜单/按钮）',
    level: '权限级别',
    roleCode: '角色编码',
    roleName: '角色名称',
    lv: { RW: '读写', RO: '只读', HIDDEN: '隐藏' }
  }
}
