-- =============================================================
-- UAT 六轮批次：
--  1) 制度全文与原件：policy 增加 docx 原件三列（全文沿用已有 content 列，
--     上传 .docx 时由 POI 提取全文写入 content，原件字节 + sha256 固化留档）
--  2) 法规-制度映射 AI 符合度评估：结论/详情/评估时间/失效标记（法规再变更时置 stale）
--  3) 通知规则引擎：回执表增加渲染后的消息与来源规则 id；
--     旧规则种子为纯展示配置（引擎不可执行），替换为结构化规则
-- 注意：本文件的注释与字符串里都不能出现 美元符+花括号 字面量（Flyway 会当占位符解析报错），
--       因此通知内容模板的变量一律采用 {变量名} 花括号语法，引擎按同样语法渲染。
-- =============================================================

-- 1) 制度 docx 原件（content 全文列已存在）
ALTER TABLE policy ADD COLUMN doc_name   VARCHAR(256);
ALTER TABLE policy ADD COLUMN doc_sha256 VARCHAR(64);
ALTER TABLE policy ADD COLUMN doc_bytes  BYTEA;

-- 2) 法规-制度映射：AI 符合度评估结果
ALTER TABLE regulation_policy_map ADD COLUMN assess_verdict VARCHAR(16);
ALTER TABLE regulation_policy_map ADD COLUMN assess_detail  TEXT;
ALTER TABLE regulation_policy_map ADD COLUMN assessed_at    TIMESTAMPTZ;
ALTER TABLE regulation_policy_map ADD COLUMN assess_stale   BOOLEAN NOT NULL DEFAULT FALSE;

-- 3) 通知规则引擎：回执携渲染消息与来源规则
ALTER TABLE reminder_dispatch_log ADD COLUMN message TEXT;
ALTER TABLE reminder_dispatch_log ADD COLUMN rule_id BIGINT;

-- 旧 RULE 种子仅是展示性文案，替换为引擎可执行的结构化规则（detail 为 JSON：
-- source=数据源，days=条件天数/窗口，channel=通道，template=含 {变量} 的内容模板）
DELETE FROM notify_config WHERE kind = 'RULE';
INSERT INTO notify_config (org_id, kind, name, detail, enabled) VALUES
  (1, 'RULE', '整改逾期提醒',
   '{"source":"REMEDIATION_OVERDUE","days":0,"channel":"INBOX","template":"整改单「{标题}」已逾期 {逾期天数} 天（责任人：{责任人}），请尽快处理并提交整改证据。"}', TRUE),
  (1, 'RULE', '评估复核滞留提醒',
   '{"source":"ASSESSMENT_STALLED","days":3,"channel":"INBOX","template":"风险评估「{标题}」待复核已滞留 {滞留天数} 天，请复核人尽快处理。"}', TRUE),
  (1, 'RULE', '法规新采集通报',
   '{"source":"REG_NEW","days":1,"channel":"INBOX","template":"追踪源新采集法规「{标题}」（{发布机构}），请合规岗评估适用性并建立制度映射。"}', TRUE),
  (1, 'RULE', 'KRI 阈值告警',
   '{"source":"KRI_BREACH","days":0,"channel":"INBOX","template":"关键风险指标「{指标}」最新值 {数值}{单位} 触及【{级别}】阈值，请风险岗核查处置。"}', TRUE);

-- 4) AI 提示词模板：制度符合度评估（治理页可改；同名启用模板优先于内置口径）
INSERT INTO ai_governance (kind, name, detail, enabled, updated_at, updated_by)
VALUES ('PROMPT_TEMPLATE', '制度符合度评估',
        '请以合规官视角，对比下述监管法规要求与企业制度全文，输出三部分（中文、条款级、简明）：1、结论（严格三选一：符合/部分符合/不符合）；2、差距说明（逐条指出制度未覆盖或与法规冲突之处）；3、建议修订点（给出应新增或修改的制度条款方向）。',
        TRUE, now(), 'system');
