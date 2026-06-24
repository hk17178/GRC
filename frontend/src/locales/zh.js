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
    perm: { g: '系统管理', c: '权限与审批' },
    board: { g: '系统管理', c: '看板与留痕' },
    feedback: { g: '系统管理', c: '建议与反馈' },
    settings: { g: '系统管理', c: '系统设置' }
  },

  // ---- 全局浮动 AI 助手按钮 ----
  aiFab: {
    title: 'AI 助手'
  },

  // ---- 占位页（尚未复原的菜单项，点击进入此页保证不报错）----
  placeholder: {
    tag: '建设中',
    title: '{name}',
    desc: '该模块原型尚未复原，敬请期待。当前可正常导航、主题与语言切换均生效。'
  },

  // ---- 外部审计（M3-EXT，严格对齐驾驶舱版原型 #view-extaudit）----
  extaudit: {
    tag: 'M3-EXT · 外部审计',
    title: '外部审计',
    register: '＋ 登记外审任务',
    // 子公司分段
    seg: { all: '集团', pay: '支付科技', consumer: '消费金融', tech: '数科' },
    // 三个 Tab
    tab: { tasks: '外审任务', findings: '外部审计发现', remed: '整改跟踪' },
    // KPI
    kpi: {
      active: '进行中外审',
      bodies: '外部审计机构',
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
      th: { item: '报送事项', regulator: '监管机构', type: '类型', deadline: '法定时限', owner: '责任人', status: '状态' },
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
      th: { id: '编号', regulator: '监管机构', subject: '事项', received: '收文日', replyDue: '答复期限', replyLog: '答复留痕', status: '状态' },
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
      th: { id: '编号', type: '类型', regulator: '监管机构', reason: '事由', date: '日期', remediation: '整改要求', replyStatus: '回函状态' },
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
      emptyDesc: '发生重大事件时，从此处发起监管报送并跟踪回执；与安全事件、整改、证据留痕联动。'
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
      stOverdue: '逾期'
    }
  },

  // ---- 风险评估（M2，严格对齐驾驶舱版原型 #view-risk 及其下钻视图）----
  risk: {
    tag: 'M2 · 风险评估',
    title: '风险评估闭环',
    newAssess: '＋ 发起评估',
    // 顶部 Tab
    tab: {
      tasks: '评估任务',
      templates: '模板库',
      controls: '统一控件库',
      kri: 'KRI 监控'
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
      th: { id: '编号', ctrl: '控制点', systems: '覆盖体系', reuse: '复用', result: '结果' },
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
      kpi: { metrics: '监控指标', breach: '超阈值预警', sources: '采集源', sourcesSub: 'SIEM/日志/漏洞', collect: '采集状态', collectV: '正常' },
      title: 'KRI 指标与阈值',
      config: '＋ 配置指标',
      th: { metric: '指标', source: '采集源', current: '当前', threshold: '阈值', status: '状态' },
      rows: {
        vuln: { metric: '核心系统漏洞修复时效', source: '漏洞管理', current: '23.4 天', threshold: '≤15 天', status: '超阈值' },
        priv: { metric: '特权账号未审查数', source: 'SIEM', current: '7', threshold: '=0', status: '紧急' },
        log: { metric: '日志留存天数', source: '日志平台', current: '162 天', threshold: '≥180 天', status: '关注' }
      },
      st: { over: '超阈值', urgent: '紧急', watch: '关注' }
    },
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
