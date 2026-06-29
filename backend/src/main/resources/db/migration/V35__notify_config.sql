-- =============================================================
-- V35 · 通知中心配置（补回 UAT 反馈缺失的"通知场景/规则/通道"等配置）
-- =============================================================
-- 单表承载三类配置（kind 区分），detail 存各类专有字段(JSON)，降低样板：
--   SCENARIO 通知场景：{trigger 触发, receiver 接收角色/层级, contentPoints 内容要点, channel 通道}
--   RULE     通知规则：{triggerEvent 触发事件, level 级别(NORMAL/URGENT), channel 通道}
--   CHANNEL  通道：    {type 类型(EMAIL/SMS/WECOM), target 目标/机器人}
-- 携 org_id + RLS，与业务表同口径。
-- =============================================================

CREATE TABLE notify_config (
  id         BIGSERIAL    PRIMARY KEY,
  org_id     BIGINT       NOT NULL,
  kind       VARCHAR(16)  NOT NULL,                 -- SCENARIO / RULE / CHANNEL
  name       VARCHAR(128) NOT NULL,
  detail     TEXT,                                  -- JSON：各 kind 专有字段
  enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE notify_config ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_notify_config_iso ON notify_config
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON notify_config TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE notify_config_id_seq TO grc_app;

-- 种子：org12 几条示例（演示场景/规则/通道）
INSERT INTO notify_config (org_id, kind, name, detail, enabled) VALUES
 (12, 'SCENARIO', '外审计划临近',
  '{"trigger":"计划开始前 N 天（可配 15/10 天）","receiver":"外审责任单位 + 指定企微机器人","contentPoints":"外审任务·机构·计划开始日·剩余天数·跳转","channel":"WECOM"}', TRUE),
 (12, 'SCENARIO', '残余高风险待接受',
  '{"trigger":"风险评估残余高/极高且未接受","receiver":"风险责任人 + 分管领导","contentPoints":"评估对象·残余等级·处置建议·跳转","channel":"EMAIL"}', TRUE),
 (12, 'RULE', '整改逾期升级',
  '{"triggerEvent":"整改任务超期未闭环","level":"URGENT","channel":"SMS"}', TRUE),
 (12, 'RULE', '制度发布通知',
  '{"triggerEvent":"制度审批通过生效","level":"NORMAL","channel":"EMAIL"}', TRUE),
 (12, 'CHANNEL', '外审通知群机器人', '{"type":"WECOM","target":"https://qyapi.weixin.qq.com/...（部署配置）"}', TRUE),
 (12, 'CHANNEL', '合规邮件通道', '{"type":"EMAIL","target":"compliance-notify@example.com"}', TRUE);
