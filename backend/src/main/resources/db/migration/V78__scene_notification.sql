-- =============================================================
-- V78 · D1-8 §九 接线二：内核消费自定义通知场景 + 升级链运行态
-- =============================================================
-- §九（V75）定义了「设计态场景库 → 运行态装配 + 升级链」，但只到 assemble() 装配结果为止。
-- 本迁移补上运行态落地：通知规则引擎产出新告警时，按 (org, 事件类型) 消费本组织已装配场景，
-- 为每个命中场景生成一条 scene_notification（记接收角色 + 消息 + 单据引用 + 当前升级级别）；
-- 升级链运行器按延迟逐级触发，写 scene_escalation_log。
--
-- 隔离：两表均携 org_id + RLS。内核在 setAllOrgs 会话下消费，但消费/升级一律显式按 org_id 过滤，
-- 绝不把 A 组织的告警投递到 B 组织的场景——跨子公司永不外溢。
-- =============================================================

-- 场景通知（运行态：一条告警 × 一个命中场景 = 一条）
CREATE TABLE scene_notification (
  id              BIGSERIAL    PRIMARY KEY,
  org_id          BIGINT       NOT NULL,                    -- 隔离锚点（= 告警所属组织）
  scene_id        BIGINT       NOT NULL REFERENCES notification_scene(id),
  event_type      VARCHAR(64)  NOT NULL,                    -- 触发事件类型（RULE_*/MAJOR_INCIDENT_REPORT_DUE…）
  object_type     VARCHAR(48)  NOT NULL,                    -- 单据类型（REMEDIATION/KRI_MEASUREMENT…）
  object_id       BIGINT       NOT NULL,                    -- 单据 id
  message         TEXT         NOT NULL,                    -- 已渲染的告警消息（承自规则引擎）
  recipient_roles TEXT         NOT NULL,                    -- 场景装配的接收角色 JSON（快照）
  current_level   INT          NOT NULL DEFAULT 0,          -- 已升级到的级别（0=首发未升级）
  status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',  -- PENDING（待处理，会升级）/ ACKED（已确认，停升级）
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  acked_at        TIMESTAMPTZ,
  -- 幂等：同一场景对同一单据同一事件只生成一条（引擎重复评估不重复通知）
  CONSTRAINT uq_scene_notification UNIQUE (scene_id, object_type, object_id, event_type)
);
CREATE INDEX idx_scene_notification_pending ON scene_notification(org_id, status);

ALTER TABLE scene_notification ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_scene_notification_iso ON scene_notification
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON scene_notification TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE scene_notification_id_seq TO grc_app;

-- 升级触发留痕（运行器每触发一级写一条）
CREATE TABLE scene_escalation_log (
  id               BIGSERIAL    PRIMARY KEY,
  org_id           BIGINT       NOT NULL,                   -- 隔离锚点（随宿主通知同组织）
  notification_id  BIGINT       NOT NULL REFERENCES scene_notification(id),
  level            INT          NOT NULL,                   -- 触发的升级级别
  escalate_to_role VARCHAR(48)  NOT NULL,                   -- 升级到的角色
  message          TEXT         NOT NULL,
  fired_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  -- 幂等：同一通知同一级别只触发一次（运行器重复扫描不重复升级）
  CONSTRAINT uq_scene_escalation UNIQUE (notification_id, level)
);
CREATE INDEX idx_scene_escalation_org ON scene_escalation_log(org_id, notification_id);

ALTER TABLE scene_escalation_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_scene_escalation_iso ON scene_escalation_log
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT ON scene_escalation_log TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE scene_escalation_log_id_seq TO grc_app;
