# GRC 平台 · 三层对齐审计报告（设计 ↔ 后端 ↔ 前端）

**文档编号：** DM-5 ｜ **版本：** v1.0 ｜ **日期：** 2026-06-24
**范围：** 已完成的 6 个业务模块（M1 制度 / M2 风险评估 / M3 审计管理 / M6 组织资产 / M8 权限审批 / M11 监管事项）
**方法：** workflow 并行,每模块一个审计 agent 同时读取该模块的【设计文档 / 后端代码 / 前端页面】,逐项核对 实体·字段·枚举·状态机·红线·API集成 六维一致性。
**结论：** 共 **52 条差距**。**最严重的是系统性的:前后端完全未联调(6/6 模块前端零 API 调用,全为原型静态假数据)**,其余为枚举级数/状态命名/字段缺失/红线语义/能力缺口的三层不一致。

> 本报告承接用户提出的关键修正:验收"对齐"不能只比原型,必须包含 **前端↔后端** 与 **前端/后端↔设计功能逻辑** 的一致性。

---

## 一、头号系统性问题:前后端两条平行线,从未相接

**6 个模块全部 `integrationWired = false`** —— `frontend/src` 全仓 grep 无任何 `fetch/axios/api` 调用;所有页面数据都是 `.vue` 里写死的静态数组(原型示例值)。后端 REST API 全部就绪却无人调用。

| 模块 | 前端 | 后端 API | 联调 |
|---|---|---|---|
| M1 制度 | 占位页 | 8 端点就绪 | ❌ |
| M2 风险评估 | 真实页(静态) | 11 端点就绪 | ❌ |
| M3 审计管理 | 真实页(静态,仅外审) | 审计计划+发现 端点就绪 | ❌ |
| M6 组织资产 | 真实页(静态) | orgs/assets/ropa 端点就绪 | ❌ |
| M8 权限审批 | 占位页 | permissions/access-reviews 就绪 | ❌ |
| M11 监管事项 | 真实页(静态) | 四台账 端点就绪 | ❌ |

→ 这是"对齐"的根本缺口:**界面与后端的数据/状态/红线根本没有打通**,前端展示的全是假象(KPI、列表、漏斗、预警均硬编码)。

---

## 二、其余差距按类别（详见 workflow 输出全量 52 条）

### A. 枚举级数/取值不一致（平台基线常被偏离）
| 模块 | 设计基线 | 后端实现 | 前端 |
|---|---|---|---|
| M3 审计严重度 | 五级 VERY_LOW…VERY_HIGH | **四级 LOW/MID/HIGH/CRITICAL** | 五级标签 |
| M2 风险发现状态 | IN_TREATMENT | **TREATING** | 不展示 |
| M6 资产分级 | 三级 公开/内部/敏感 | **四级 PUBLIC/INTERNAL/CONFIDENTIAL/SECRET** | 两级展示 |
| M11 重大事件 severity | 五级 | **自由文本无约束** | 空态 |
| M8 UAR 结论 | KEEP/REVOKE/**DOWNGRADE** | 缺 DOWNGRADE | 占位 |

### B. 状态机命名不一致
- M1 制度:后端 `DRAFT/PENDING_APPROVAL/PUBLISHED/ARCHIVED` vs 设计 `DRAFT/REVIEW/EFFECTIVE/DEPRECATED`
- M3 计划:后端 `REPORTED`(vs 设计 `REPORTING`)、缺 `CANCELLED`;漏斗 `CONFIRMED_CLOSED`(vs 设计 `CLOSED`)
- M11 报送:后端 `PLANNED/PREPARING/SUBMITTED/ACCEPTED` vs 设计 `TO_DRAFT/DRAFTING/SUBMITTED/CLOSED`;问询后端三态 vs 设计/前端四态

### C. 后端缺字段（设计要求 / 前端已展示,但后端无列）
- M2:`treatment_decision`(降低/接受/转移/规避,后端只有自由文本)、`atv_id/risk_score`、接受 `evidence_id`
- M3:三段时间戳 `submitted/accepted/closed_at`、依据条款 `clause_ref`、`evidence_id`、`external_body`、认证体系/周期/责任单位
- M6:`CIA`、`network_zone`、等保 `mlps_level/record/cycle`(被压成单 boolean)、ROPA `数据量级/是否敏感/接收方`、owner 应为用户外键
- M11:报送 `type/owner/回执留痕`、问询 `答复留痕`、处罚 `reason/remediation_req/reply_status`、重大事件 `report_deadline/version`
- M8:`signed_hash/tsa_token`(UAR 签认证据)、`platform_disabled`(账号级平台停用)、`role.active`(角色停用)

### D. 红线语义问题（最该优先核对）
- **M8 SoD 语义反了**:设计要求"**检测型默认(DETECT)+ 仅 `enforce_mode=BLOCK` 单配硬阻断**",后端把**所有规则一律硬阻断**,缺 DETECT"放行+登记冲突"路径。← 需修
- M2 关闭门控:方向正确(已落地✅),但"有效接受"判定偏弱(未校验 evidence/版本),前端未体现门控禁用态
- M3 外审漏斗:门控强制到位✅,但未产 `EXT_AUDIT_RESPONSE_*` 事件、缺 `EXTERNAL_RESPONSE_DUE` 回函时限预警

### E. 后端缺大能力（前端有 UI / 设计要求,后端零实现）
- M2:**KRI 监控 / 模板库 / 统一控件库 / A-T-V 建模**(四大能力前端全套展示,后端无)
- M8:**统一多级审批流引擎(Flowable,加签/转办/超时)** —— M8 的"审批"半边几乎完全缺失
- M1:知识库(kb_chunk/pgvector)、版本化(policy_version)、签署确认闭环(强制范围/逾期/重签)
- M11:年度合规计划(前端整页,后端无实体)
- M6:AD 同步(前端显示"已同步正常",后端无对接)

### F. 设计内部矛盾（需先消歧再对齐）
- M8 UAR 结论:`D1-3`(三态 KEEP/REVOKE/DOWNGRADE)与 `需求 10.6.1`(两态)不一致 —— 设计层自身要先定。

---

## 三、分阶段修复 + 集成计划

> 原则:**以设计基线为准对齐**;实现确有更优者(如命名)则反向回写设计并登记;确未实现的大能力明确排期或排除,杜绝"前端假象"。

### Stage 1 · 基线对齐 + 红线修正（P0,先做,成本低收益高）
- 枚举/状态改齐设计基线:M3 严重度四级→五级;M2 `TREATING→IN_TREATMENT`;M6 分级四级→三级;M11 重大事件 severity 五级化、四台账状态机改齐;M3 计划/漏斗命名与 `CANCELLED`。
- **修 M8 SoD 红线语义**:加 `enforce_mode`(DETECT 默认/BLOCK),改为"检测登记 + 仅 BLOCK 硬阻断"。
- M2 关闭门控强化"有效接受"校验。
- 每项配迁移 + 测试,DinD 跑绿。涉及设计取舍处同步回写 D1-2/D1-4 并消歧 F 类矛盾。

### Stage 2 · 前后端集成打通（P0,核心"对齐"动作）
- 建前端 API 客户端(axios/fetch + X-User 桩 + 统一错误处理)。
- 把 4 个真实页(M2/M3/M6/M11)从静态数组改为**真调后端 API**;补建 M1/M8 真实页(或明确延后)。
- 补 C 类后端缺字段(随集成需要逐模块补)。
- **端到端 Chrome 验收**:UI 驱动后端,红线实时生效(如残余高风险无接受时关闭按钮禁用)。

### Stage 3 · 大能力补齐 / 排期（P1-P2）
- M8 审批流引擎、M2 KRI/模板库/控件库/A-T-V、M1 知识库/版本化、M11 年度合规计划、M6 AD 同步等:逐一决策"本期建 / 延后",写入 D1-9 与开发计划;前端对未实现能力改为占位/禁用,消除假象。

### 标准升级
- D2-5 验收标准升级为**三方对齐**:每模块"跑绿" = 原型保真 + 前端↔后端联调 + 三层(设计/后端/前端)功能逻辑一致。

---

## 四、价值说明
本审计验证了"只比原型不够"的判断:并行建设产出了两条**各自自洽、却互不相接且偏离设计**的轨道。差距在**继续铺更多模块之前**被系统性查清,避免了错位规模化。全量 52 条差距见 workflow 输出(run `wf_671b0515-85d`)。
