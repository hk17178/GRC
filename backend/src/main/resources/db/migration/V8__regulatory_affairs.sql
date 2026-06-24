-- =====================================================================
-- V8 · M11 监管事项（4 台账 + 报送日历，落地"法定时限预警"红线）
-- 设计依据：需求文档 M11 监管事项（报送日历/监管问询/处罚约谈/重大事件报送/年度合规计划）、
--           D1-2 §23（reg_filing/reg_inquiry/reg_penalty/major_incident_report）、D2-5、
--           D1-1 §5.12（调度内核为时间事件唯一生产者）、D1-9 H-01。
-- 要点：
--   1) 四张业务台账，隔离锚点 org_id，均 ENABLE RLS + USING/WITH CHECK（按 visible_orgs 裁剪含写入校验）；
--      GRANT SELECT/INSERT/UPDATE 与 BIGSERIAL 序列 GRANT USAGE,SELECT 给 grc_app（与 V3/V6 同口径）。
--   2) reg_filing（报送日历）是【法定时限预警】红线的到期源：statutory_deadline + reminder_days，
--      与 V3 audit_plan 同构——ExpiryScanService.scanOnce 新增一段扫描它，命中 reminder_days 某天即产
--      REG_FILING_DUE 事件，复用 reminder_dispatch_log 幂等台账(object_type='REG_FILING') 与 domain_event 件箱。
--      reminder_days 给库级 DEFAULT '{15,10}'（与 audit_plan 一致），由 Service 显式 INSERT 时按默认填充，
--      实体不映射该数组列（避免 Hibernate 处理 PG 数组类型的额外配置），交由库默认值保障调度可用。
--   3) 状态字段均给 NOT NULL DEFAULT，时间戳 created_at/updated_at NOT NULL DEFAULT now()（实体 @PrePersist/@PreUpdate 维护）。
-- 注：本迁移由 Flyway 以 owner 角色执行，PostgreSQL 原生 `::` 强转在此合法；
--    但 Service 层 EntityManager 原生查询禁止用 `::`，统一用 CAST（见 M1~M3 范式注记，本任务 ExpiryScanService 已遵循）。
-- =====================================================================

-- ---------- 报送日历（到期源；法定时限预警红线）----------
-- status 状态机：TO_DRAFT → DRAFTING → SUBMITTED → CLOSED
-- statutory_deadline + reminder_days 为调度内核到期源（命中 reminder_days 某天产 REG_FILING_DUE）。
CREATE TABLE reg_filing (
  id                  BIGSERIAL PRIMARY KEY,
  org_id              BIGINT       NOT NULL REFERENCES org(id),       -- 隔离锚点
  title               VARCHAR(256),                                   -- 报送事项标题
  regulator           VARCHAR(64),                                    -- 监管机构
  statutory_deadline  DATE         NOT NULL,                          -- 法定报送时限（到期源锚点）
  reminder_days       INT[]        NOT NULL DEFAULT '{15,10}',        -- 报送前 N 天预警（与 audit_plan 同构）
  status              VARCHAR(24)  NOT NULL DEFAULT 'TO_DRAFT',       -- 报送生命周期状态机当前态（TO_DRAFT/DRAFTING/SUBMITTED/CLOSED）
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_reg_filing_org ON reg_filing(org_id);
CREATE INDEX idx_reg_filing_deadline ON reg_filing(statutory_deadline);

ALTER TABLE reg_filing ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_reg_filing_iso ON reg_filing
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON reg_filing TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE reg_filing_id_seq TO grc_app;

-- ---------- 监管问询台账 ----------
-- status 状态机：DRAFTING → REPLIED → AWAIT_FEEDBACK → CLOSED
CREATE TABLE reg_inquiry (
  id             BIGSERIAL PRIMARY KEY,
  org_id         BIGINT       NOT NULL REFERENCES org(id),            -- 隔离锚点
  title          VARCHAR(256),                                        -- 问询事项标题
  regulator      VARCHAR(64),                                         -- 监管机构
  received_date  DATE,                                                -- 收到问询日
  due_date       DATE,                                                -- 答复截止日
  status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFTING',            -- 问询处置状态机当前态（DRAFTING/REPLIED/AWAIT_FEEDBACK/CLOSED）
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_reg_inquiry_org ON reg_inquiry(org_id);

ALTER TABLE reg_inquiry ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_reg_inquiry_iso ON reg_inquiry
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON reg_inquiry TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE reg_inquiry_id_seq TO grc_app;

-- ---------- 处罚约谈台账 ----------
-- status 状态机：OPEN → RECTIFYING → CLOSED
CREATE TABLE reg_penalty (
  id             BIGSERIAL PRIMARY KEY,
  org_id         BIGINT       NOT NULL REFERENCES org(id),            -- 隔离锚点
  title          VARCHAR(256),                                        -- 处罚/约谈事项标题
  regulator      VARCHAR(64),                                         -- 监管机构
  penalty_type   VARCHAR(32),                                         -- 处罚类型（罚款/警告/约谈等）
  amount         NUMERIC,                                             -- 罚没金额（可空）
  occurred_date  DATE,                                                -- 发生日
  status         VARCHAR(16)  NOT NULL DEFAULT 'OPEN',                -- 处置状态机当前态
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_reg_penalty_org ON reg_penalty(org_id);

ALTER TABLE reg_penalty ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_reg_penalty_iso ON reg_penalty
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON reg_penalty TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE reg_penalty_id_seq TO grc_app;

-- ---------- 重大事件报送台账 ----------
-- status 状态机：DRAFT → REPORTED → CLOSED
CREATE TABLE major_incident_report (
  id            BIGSERIAL PRIMARY KEY,
  org_id        BIGINT       NOT NULL REFERENCES org(id),             -- 隔离锚点
  title         VARCHAR(256),                                         -- 事件标题
  severity      VARCHAR(12)
                  CHECK (severity IN ('VERY_LOW','LOW','MID','HIGH','VERY_HIGH')),  -- 严重度（平台五级；与 RiskLevel/AuditSeverity 同口径）
  occurred_at   TIMESTAMPTZ,                                          -- 事件发生时刻
  reported_at   TIMESTAMPTZ,                                          -- 上报监管时刻
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',                -- 报送状态机当前态
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_major_incident_org ON major_incident_report(org_id);

ALTER TABLE major_incident_report ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_major_incident_iso ON major_incident_report
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON major_incident_report TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE major_incident_report_id_seq TO grc_app;
