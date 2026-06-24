# GRC 平台 · 后端补全开发计划与 Backlog（D2-6）

**文档编号：** D2-6 ｜ **版本：** v1.0 ｜ **日期：** 2026-06-24
**性质：** 执行路线图（后端先行阶段的 plan-of-record）
**依据：** 用户 2026-06-24 决策"后端先行·按模块补全·分两批"（见会话）+ DM-5 三层对齐审计 + D1-7 界面交互规格 + GRC平台建设需求文档

---

## 一、背景与策略结论

**现状盘点（2026-06-24）**：后端 14 控制器 / 17 服务 / 20 实体 / 84 端点 / 9 迁移。
- 横向红线地基（隔离 RLS / 防篡改哈希链 / 调度内核 / 全局异常映射）≈90%；
- 6 模块核心生命周期+红线（M1 制度 / M2 风险 / M3 外审 / M6 组织资产 / M8 权限 / M11 监管）各 ≈45–55%；
- 8 未建菜单 + 4 横切聚合 ≈5%；Flowable 审批、AI+pgvector = 0%。
- **总体后端 ≈35–40%**（已建为最难核心）。

**策略结论**：转为**后端先行、按模块纵向补全（测试驱动）**为主线，前端联调收敛为**里程碑式 + 最终一次性 Chrome 全量验收**。理由：前端功能真源=后端，后端缺则前端只能占位+反复返工；后端可纯靠 Testcontainers 快速推进。**否决纯大爆炸**（全后端→最后才前端），保留每模块自测跑绿+三层对齐自查+里程碑集成冒烟。

**两个分叉（用户拍板）**：① 范围=分两批·核心优先；② Flowable=Phase A 最先接。

---

## 二、阶段划分

| 阶段 | 目标 | 集成冒烟 |
|---|---|---|
| **A** | 补全 6 已建模块到设计完整 + Flowable 审批引擎 | 阶段末一次 Chrome 冒烟 |
| **B** | 未建模块后端 + 横切聚合 API | — |
| **C** | AI 引擎（pgvector + 检索问答 + 模型接入） | 阶段末一次 Chrome 冒烟 |
| **D** | 前端系统性接通 + Chrome 全量验收（完整性/可用性/功能逻辑/红线可见/隔离），UAT 前最后门禁 | 全量 |

每阶段铁律（D2-5）：全中文注释 + Testcontainers 跑绿 + CI 绿才提交；红线用例 100% 覆盖。

---

## 三、Phase A Backlog（当前阶段）

### A0 · Flowable 审批工作流内核 —— ✅ 已完成（2026-06-24）
- `flowable-spring-boot-starter-process` 7.0.1；共用 grc_app 数据源+Spring 事务（审批与业务同事务原子）；
- V10 授 grc_app CREATE（引擎自建 ACT_*，无 org_id/不挂 RLS，仅编排）；async-executor 关、IDM 关；
- `generic-approval.bpmn20.xml` 通用单级审批流；`WorkflowService`（submit/pendingTasks/decide/outcome/isEnded）+ 哈希链留痕；
- `WorkflowEngineTest` 3/3 绿（部署/通过/驳回/留痕）。

### A1 · M1 制度体系
- [x] 制度发布走 Flowable 审批 —— ✅ 提交评审启动 genericApproval；审批结论驱动 REVIEW→EFFECTIVE/退回 DRAFT，审批与状态流转同事务原子 + 留痕；PolicyLifecycleTest 9/9 绿（含工作流联动用例）。
- [ ] 知识库（分类、检索）
- [ ] 版本管理与差异（version/diff、生效/废止）

### A2 · M2 风险评估
- [x] 风险接受走 Flowable 审批 —— ✅ 改「申请→审批」两段式：申请置 PENDING 不放行，审批**通过才回填**
  finding.risk_acceptance_id（CR-002 门控解除），驳回不放行。红线更硬（高残余关闭须经审批通过的接受）。
  RiskAssessmentTest 8/8（含"申请后未审批仍拦截""驳回门控保持""留痕 8 条"）、RiskCloseGateWebTest 2/2 绿。
  ⚠️ 前端 CR-002「登记风险接受」按钮调旧 /accept，已改为 /request-acceptance + /accept-approve 两步，
     需 Phase D 重接（当前 grc-int-app 该按钮会 404，属 backend-first 预期）。
- [x] KRI 监控 —— ✅ 新模块 modules/kri：指标(双阈值+方向 UPPER_BAD/LOWER_BAD)+ 测量时序，
  每次测量按方向评定 NORMAL/WARNING/CRITICAL 并回写最近态，CRITICAL 即红线触发并留痕；
  RLS 隔离 + 哈希链留痕；/api/kris CRUD+测量。KriMonitoringTest 4/4 绿。
- [x] 统一控件库 —— ✅ 新模块 modules/control：控制项 + 一控多框架映射(MLPS/ISO27001/PCI_DSS/PBOC)，
  同框架同条款判重、停用状态机；RLS 隔离 + 留痕。/api/controls CRUD+映射+停用。ControlLibraryTest 4/4 绿。
- [x] 评估模板库 + 评估-控件复用 —— ✅ modules/assessment 增模板(框架/状态机 DRAFT→PUBLISHED→RETIRED)
  + 模板项(引用控件) + 实例化(→ 新建评估 + 评估项逐条拷贝含 control_id 复用) + 逐项评估(符合性结论)。
  发布/实例化门控；RLS 隔离 + 留痕。/api/assessment-templates + /api/assessment-items。TemplateLibraryTest 4/4 绿。
- [x] A-T-V（资产-威胁-脆弱）—— ✅ 新模块 modules/atv：威胁库 + 脆弱性库 + 风险场景(资产×威胁×脆弱)，
  固有等级由可能性×影响经风险矩阵派生平台五级；创建场景经 AssetService 校验资产可见(桥接 M6)；
  组合判重、重评重算、越界校验；RLS 隔离 + 留痕。AtvRiskScenarioTest 5/5 绿。

> **A2(M2 风险评估)全部完成**：风险接受审批 / KRI / 统一控件库 / 模板库+实例化 / A-T-V。

### A3 · M3 审计管理
- [x] 内部审计纳入 —— ✅ 既有 AuditType 已含 INTERNAL/EXTERNAL/REGULATORY，内审为一等类型；外审漏斗仅外审可用。
- [x] 整改工单（finding→整改任务→验证闭环）—— ✅ 新增 RemediationOrder(派单→开始→提交→验证，
  退回返工)；**验证闭环红线**：发现须有 ≥1 条 VERIFIED 工单方可标记已整改(AuditFindingService.remediate 强制)。
  RLS 隔离 + 留痕。/api/remediation-orders。RemediationOrderTest 4/4，既有内审用例同步改造，全量 101/101 绿。

### A4 · M8 权限与审批
- [x] SoD 例外走 Flowable 审批 —— ✅ SoD 豁免改「申请→审批」两段式：申请置 PENDING（不放行），
  **审批通过才置 APPROVED 并被 enforceSod 视为有效豁免**，故 BLOCK 互斥授权须经审批通过的豁免方放行（红线更硬）。
  V17 加 status/requester、approver/approved_at 放宽可空。/api/permissions/sod-exceptions(申请) + /{id}/approve|reject。
  PermissionSodTest 增"仅申请未审批仍不放行"，既有"补豁免后可授予"改两步；全量 102/102 绿。
- [ ] UAR 决策走审批（可选；当前 reviewer 即审阅决策人，决策已受控）
- [ ] 审批中心查询 API（我的待办审批、按域）— 与前端「权限与审批」页一并 Phase D

### A5 · M11 监管事项
- [x] 监管报送走 Flowable 审批 —— ✅ 报送加内部复核：DRAFTING→submitForReview→PENDING_REVIEW(启动审批)
  →通过 SUBMITTED(正式报送)/驳回退回 DRAFTING。/api/reg-filings/submit-for-review|approve-submit|reject-submit。
  RegulatoryAffairsTest 改两步 + 增驳回退回用例。
- [x] 年度合规计划（计划-分解-跟踪）—— ✅ 新增 CompliancePlan + CompliancePlanItem：
  状态机 DRAFT→ACTIVE→CLOSED，计划项 PENDING→IN_PROGRESS→DONE；下发门控(须有项)、非草稿不可加项；
  RLS 隔离 + 留痕。/api/compliance-plans。CompliancePlanTest 4/4。全量 107/107 绿。
  （此即前端「年度合规计划」原占位功能的后端，Phase D 可接通。）

### A6 · M6 组织与资产
- [x] 资产-威胁-脆弱 —— ✅ 已由 A2 A-T-V(modules/atv) 覆盖（风险场景桥接 asset）。
- （AD/LDAP 同步按用户既定押到人工联测期，不在本阶段）

> **🎉 Phase A 后端补全收官**：A0 Flowable 审批引擎 · A1 制度审批 · A2 M2 全域(风险接受审批/KRI/控件库/模板库/A-T-V)
> · A3 M3 整改工单 · A4 M8 SoD 审批 · A5 M11 报送审批+年度计划。后端测试 84→**107 全绿**。
> 红线全部端到端硬化：隔离/留痕/调度/关闭门控/外审漏斗/SoD/法定时限/审批/整改验证闭环。

---

## 四、Phase B / C（batch2）

### Phase B · 横切聚合 + 未建模块
- [x] **合规态势 Dashboard 聚合** —— ✅ 新模块 modules/dashboard：GET /api/dashboard/summary 跨模块只读聚合
  （风险:未关闭/被门控/KRI预警·严重；审计:未关闭/未验证整改；监管:待报送/已报送；制度:生效/评审/草稿；权限:待审批SoD）。
  隔离天然按域(各 findAll 受 RLS)。DashboardTest 1/1（跨模块汇总 + org13 全 0 隔离）。
- [x] **看板与留痕查询** —— ✅ modules/audit 增 AuditTrailService：GET /api/audit-trail（按 对象/动作/操作人 过滤、新→旧、RLS 裁剪）
  + GET /api/audit-trail/verify?orgId=（链完整性校验，防篡改卖点）。AuditTrailTest 3/3。
- [x] **我的待办 + 通知中心** —— ✅ 新模块 modules/workbench：GET /api/workbench/todos（跨模块归并未验证整改/
  未完成合规项/待报送，RLS 按域）+ /api/workbench/notifications（调度内核 reminder_dispatch_log 经 IsolationContext
  可见组织过滤，内核表未启 RLS 故显式过滤）。WorkbenchTest 2/2。**横切聚合四件全完成**。
- [ ] 未建模块：法规跟踪（法规库/订阅/影响分析）、合规清单（义务库/落实）、第三方供应商（准入/评估/监测）、
  建议与反馈（CR-004）、系统设置（租户配置 + D1-8 可配置性）。

### Phase C · AI 接入
- [ ] AI 接入（pgvector 向量库 + 检索增强问答「AI 智能问答」+「模型接入」配置，CR-004）——需先定模型/密钥与本地部署方案。

---

## 五、验收基线

- 每模块完成跑一次**三层对齐自查**（DM-5 六维：实体/字段/枚举/状态机/红线/API 集成）。
- 红线端到端可见（隔离/关闭门控/外审漏斗/SoD/法定时限/审批）后端强制 + 前端如实表达（Phase D）。
- Phase A 末、C 末各一次 Chrome 冒烟；Phase D 全量 Chrome 验收后进入统一人工测试/UAT（D3-2）。
