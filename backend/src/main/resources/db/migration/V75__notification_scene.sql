-- =============================================================
-- V75 · D1-8 §九 自定义通知场景（收官）
-- =============================================================
-- 设计态 notif_scene_def（全局场景库：事件集→可装配的场景种类，平台预置+可扩展，无 org_id）
--   → 运行态 notification_scene（各组织装配：事件集→角色/层级→模板→通道→org_scope，org_id+RLS）
--   + notification_escalation（升级链：级别→延迟小时→升级角色，org_id+RLS）。
-- 新增场景无需改码：管理员从场景库挑一个 def，配上本组织的角色/模板/通道即成一个运行态场景。
-- 不跨子公司广播：notification_scene 携 org_id + RLS，org_scope 仅 SELF/SUBTREE（本组织及其下级），
--   装配的接收人始终在本组织自有子树内——绝不外溢到兄弟子公司。M10 消费此装配结果。
-- =============================================================

-- 场景库（设计态，全局字典，类似 resource catalog）
CREATE TABLE notif_scene_def (
  id          BIGSERIAL    PRIMARY KEY,
  code        VARCHAR(48)  NOT NULL UNIQUE,               -- 场景种类唯一码
  name        VARCHAR(128) NOT NULL,
  event_types TEXT         NOT NULL,                       -- JSON 数组：该场景涵盖的事件类型集
  description VARCHAR(256),
  builtin     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE ON notif_scene_def TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE notif_scene_def_id_seq TO grc_app;

-- 运行态装配（各组织独立，RLS 隔离）
CREATE TABLE notification_scene (
  id              BIGSERIAL    PRIMARY KEY,
  org_id          BIGINT       NOT NULL,                   -- 隔离锚点
  scene_def_id    BIGINT       NOT NULL REFERENCES notif_scene_def(id),
  name            VARCHAR(128) NOT NULL,
  recipient_roles TEXT         NOT NULL,                   -- JSON 数组：接收角色 code（在本组织子树内解析）
  template        TEXT         NOT NULL,                   -- 消息模板（占位符复用 M10 规则口径）
  channel_type    VARCHAR(16)  NOT NULL DEFAULT 'INBOX',   -- INBOX / WECOM
  org_scope       VARCHAR(16)  NOT NULL DEFAULT 'SELF',    -- SELF（仅本组织）/ SUBTREE（本组织及下级部门）——绝不跨子公司
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / RETIRED
  created_by      VARCHAR(64),
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_scene_org ON notification_scene(org_id, status);

ALTER TABLE notification_scene ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_notification_scene_iso ON notification_scene
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON notification_scene TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE notification_scene_id_seq TO grc_app;

-- 升级链（附着于某个运行态场景，RLS 隔离）
CREATE TABLE notification_escalation (
  id              BIGSERIAL    PRIMARY KEY,
  org_id          BIGINT       NOT NULL,                   -- 隔离锚点（随宿主 scene 同组织）
  scene_id        BIGINT       NOT NULL REFERENCES notification_scene(id),
  level           INT          NOT NULL,                   -- 升级级别 1/2/3…
  delay_hours     INT          NOT NULL,                   -- 距上一级/首发多少小时未处理则升级
  escalate_to_role VARCHAR(48) NOT NULL,                   -- 升级到的角色 code
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_escalation_scene ON notification_escalation(org_id, scene_id, level);

ALTER TABLE notification_escalation ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_notification_escalation_iso ON notification_escalation
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON notification_escalation TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE notification_escalation_id_seq TO grc_app;

-- 平台预置场景库（对应现有 M10 事件源 + 法定红线事件；新增场景种类只需在此追加一行，不改应用码）
INSERT INTO notif_scene_def (code, name, event_types, description) VALUES
  ('REMEDIATION_CLOSURE', '整改闭环提醒', '["RULE_REMEDIATION_OVERDUE"]', '整改单逾期未闭环时提醒责任人'),
  ('ASSESSMENT_PROGRESS', '评估进度提醒', '["RULE_ASSESSMENT_STALLED","ASSET_CHANGED"]', '评估复核滞留 / 范围资产变更'),
  ('REG_DYNAMICS', '监管动态', '["RULE_REG_NEW","PERIODIC_FILING_GENERATED","REG_FILING_DUE"]', '新法规采集 / 周期报送生成 / 报送到期'),
  ('RISK_ALERT', '风险指标告警', '["RULE_KRI_BREACH"]', 'KRI 触发预警/超阈'),
  ('MAJOR_INCIDENT', '重大事件报送', '["MAJOR_INCIDENT_REPORT_DUE"]', '重大事件法定报送时限临近（红线，永不静音）'),
  ('MLPS_REVIEW', '等保测评到期', '["MLPS_REVIEW_DUE"]', '等保定级测评到期（红线，永不静音）');
