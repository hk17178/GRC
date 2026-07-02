-- =============================================================
-- V37 · M7 供应商深度（对照评估 P2：SLA 跟踪 / 事件触发复评 / 技术安全与 DPA 属性 / 评估分类）
-- =============================================================
-- 1) vendor 加技术安全/DPA 合规属性列（需求 9.3.1）。
-- 2) vendor_sla：SLA 跟踪（项/目标/实际/到期/达标）（需求 9.2）。
-- 3) vendor_incident：事件触发复评（外部负面事件 → 登记 → 复评 → 闭环）（需求 9.2/9.3.2）。
-- 4) vendor_assessment 加评估类型（首次准入/年度定期/续约/事件复评）（需求 9.3.2）。
-- 新表携 org_id + RLS。
-- =============================================================

ALTER TABLE vendor ADD COLUMN IF NOT EXISTS data_residency VARCHAR(64);          -- 数据驻留(如 境内/新加坡)
ALTER TABLE vendor ADD COLUMN IF NOT EXISTS pci_scope      BOOLEAN NOT NULL DEFAULT FALSE; -- 是否在 PCI 范围
ALTER TABLE vendor ADD COLUMN IF NOT EXISTS certifications VARCHAR(256);         -- 自身认证(ISO27001,PCI DSS…)
ALTER TABLE vendor ADD COLUMN IF NOT EXISTS dpa_signed     BOOLEAN NOT NULL DEFAULT FALSE; -- DPA 已签
ALTER TABLE vendor ADD COLUMN IF NOT EXISTS cross_border   BOOLEAN NOT NULL DEFAULT FALSE; -- 涉跨境
ALTER TABLE vendor ADD COLUMN IF NOT EXISTS sub_processing VARCHAR(256);         -- 再委托说明

ALTER TABLE vendor_assessment ADD COLUMN IF NOT EXISTS assess_type VARCHAR(16) NOT NULL DEFAULT 'ONBOARDING'; -- ONBOARDING/ANNUAL/RENEWAL/EVENT

CREATE TABLE vendor_sla (
  id         BIGSERIAL    PRIMARY KEY,
  org_id     BIGINT       NOT NULL,
  vendor_id  BIGINT       NOT NULL,
  item       VARCHAR(128) NOT NULL,           -- SLA 项(如 可用率/到达率)
  target     VARCHAR(64),                     -- 目标(≥99.9%)
  actual     VARCHAR(64),                     -- 实际(99.95%)
  due_date   DATE,                            -- 到期日期
  met        BOOLEAN      NOT NULL DEFAULT TRUE, -- 达标
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
ALTER TABLE vendor_sla ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_vendor_sla_iso ON vendor_sla
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON vendor_sla TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE vendor_sla_id_seq TO grc_app;

CREATE TABLE vendor_incident (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL,
  vendor_id     BIGINT       NOT NULL,
  event         VARCHAR(256) NOT NULL,        -- 事件(如 被曝数据泄露)
  source        VARCHAR(128),                 -- 来源(媒体/监管通报/客户投诉)
  risk_level    VARCHAR(16),                  -- 事件风险等级(五级)
  status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN', -- OPEN/REASSESSING/CLOSED
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  closed_at     TIMESTAMPTZ
);
ALTER TABLE vendor_incident ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_vendor_incident_iso ON vendor_incident
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON vendor_incident TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE vendor_incident_id_seq TO grc_app;
