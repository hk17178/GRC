-- =====================================================================
-- V3 · 调度/到期扫描内核（一期横向红线）
-- 设计依据：D1-1 §5.12（独立调度进程，只产时间事件不分发）、D1-4 调度章、D1-9 H-01。
-- 要点：
--   1) 内核是所有时间事件(*_DUE/*_EXPIRING/*_APPROACHING)的【唯一生产者】，写入 domain_event 件箱；
--   2) 幂等：reminder_dispatch_log 以 (对象,事件,阈值) 唯一约束，同一提醒只产一次；
--   3) 单实例触发：扫描时取全局 advisory 锁，多实例部署下只有一个真正扫描（防重复产）。
-- 本切片以"外审计划临近提醒(EXT_AUDIT_PLAN_APPROACHING, 15/10 天)"为演示到期源（对应 D1-7 §5.5a、TC-M3-104）。
-- =====================================================================

-- 演示到期源：外审计划（plan_start_date + reminder_days 提前提醒天数集）
CREATE TABLE audit_plan (
  id              BIGSERIAL PRIMARY KEY,
  org_id          BIGINT NOT NULL REFERENCES org(id),
  title           VARCHAR(256) NOT NULL,
  plan_start_date DATE NOT NULL,
  reminder_days   INT[] NOT NULL DEFAULT '{15,10}',     -- 计划开始前 N 天提醒
  external_status VARCHAR(24) NOT NULL DEFAULT 'PLANNED'
);
CREATE INDEX idx_audit_plan_start ON audit_plan(plan_start_date);

ALTER TABLE audit_plan ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_audit_plan_iso ON audit_plan
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON audit_plan TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE audit_plan_id_seq TO grc_app;

-- 幂等台账：保证同一 (对象类型, 对象, 事件类型, 阈值) 只产一次提醒
CREATE TABLE reminder_dispatch_log (
  id            BIGSERIAL PRIMARY KEY,
  object_type   VARCHAR(32) NOT NULL,                   -- 如 AUDIT_PLAN
  object_id     BIGINT      NOT NULL,
  event_type    VARCHAR(48) NOT NULL,                   -- 如 EXT_AUDIT_PLAN_APPROACHING
  threshold_key VARCHAR(32) NOT NULL,                   -- 如 reminder_day=10
  org_id        BIGINT      NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (object_type, object_id, event_type, threshold_key)
);

-- 事件件箱（outbox）：内核只产，消费者(M10 通知 / M9 看板)读取后置 consumed。
-- reminder_dispatch_log 与 domain_event 为系统内部表，不启用 RLS（内核跨 org 生产；
-- org_id 仅作路由列，面向用户的消费侧再按 visible_orgs 过滤）。
CREATE TABLE domain_event (
  id          BIGSERIAL PRIMARY KEY,
  event_type  VARCHAR(48) NOT NULL,
  org_id      BIGINT,
  payload     JSONB,
  produced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  consumed    BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_domain_event_unconsumed ON domain_event(consumed) WHERE consumed = false;

GRANT SELECT, INSERT ON reminder_dispatch_log TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE reminder_dispatch_log_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON domain_event TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE domain_event_id_seq TO grc_app;
