-- =============================================================
-- V47 · 内部审计 A1：发现五要素 + 审计报告（IIA IPPF / 审计署 11 号令）
-- =============================================================
-- ① 审计发现补齐国际通行五要素（4C+R）与管理层回应：
--    现状(condition) / 标准(criteria) / 原因(cause) / 影响(effect) / 建议(recommendation)
ALTER TABLE audit_finding ADD COLUMN IF NOT EXISTS condition_desc TEXT;        -- 现状：审计发现的客观事实
ALTER TABLE audit_finding ADD COLUMN IF NOT EXISTS criteria_desc  TEXT;        -- 标准：应当遵循的制度/法规/最佳实践
ALTER TABLE audit_finding ADD COLUMN IF NOT EXISTS cause          TEXT;        -- 原因：现状与标准差异的成因
ALTER TABLE audit_finding ADD COLUMN IF NOT EXISTS effect         TEXT;        -- 影响：差异带来的风险与后果
ALTER TABLE audit_finding ADD COLUMN IF NOT EXISTS recommendation TEXT;        -- 建议：审计建议
ALTER TABLE audit_finding ADD COLUMN IF NOT EXISTS mgmt_response  TEXT;        -- 管理层回应（被审计单位意见/整改承诺）
ALTER TABLE audit_finding ADD COLUMN IF NOT EXISTS response_by    VARCHAR(64); -- 回应人
ALTER TABLE audit_finding ADD COLUMN IF NOT EXISTS response_at    TIMESTAMPTZ; -- 回应时间

-- ② 审计报告：草稿(DRAFT) → 征求意见(COMMENTING) → 定稿(FINAL) → 签发(ISSUED)
--    审计意见四级：SATISFACTORY 满意 / GENERALLY_SATISFACTORY 基本满意 /
--                  NEEDS_IMPROVEMENT 需改进 / UNSATISFACTORY 不满意
CREATE TABLE audit_report (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL REFERENCES org(id),        -- 隔离锚点
  plan_id     BIGINT       NOT NULL REFERENCES audit_plan(id), -- 所属审计计划（一计划一报告）
  title       VARCHAR(256) NOT NULL,
  opinion     VARCHAR(28),                                     -- 审计意见（定稿前可空）
  summary     TEXT,                                            -- 审计概述与总体评价
  content     TEXT,                                            -- 报告正文（生成草稿后可编辑）
  status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',           -- DRAFT/COMMENTING/FINAL/ISSUED
  created_by  VARCHAR(64),
  issued_by   VARCHAR(64),                                     -- 签发人
  issued_at   TIMESTAMPTZ,                                     -- 签发时间
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_audit_report_plan UNIQUE (plan_id)
);
CREATE INDEX idx_audit_report_org ON audit_report(org_id);

ALTER TABLE audit_report ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_audit_report_iso ON audit_report
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON audit_report TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE audit_report_id_seq TO grc_app;
