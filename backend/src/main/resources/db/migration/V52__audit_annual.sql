-- =============================================================
-- V52 · 内部审计 A3：年度审计计划层 + 后续审计（follow-up）
-- 年度计划（风险导向）：年度 → 审计对象清单（风险排序/排期季度）→ 逐项生成单项审计计划。
-- 后续审计：audit_plan.follow_up_of 关联原计划，验证原发现整改有效性。
-- =============================================================

CREATE TABLE audit_annual_plan (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL REFERENCES org(id),   -- 隔离锚点
  year        INT          NOT NULL,                      -- 计划年度
  title       VARCHAR(256) NOT NULL,                      -- 如「2026 年度内部审计计划」
  status      VARCHAR(12)  NOT NULL DEFAULT 'DRAFT',      -- DRAFT/APPROVED
  approved_by VARCHAR(64),
  approved_at TIMESTAMPTZ,
  created_by  VARCHAR(64),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_audit_annual_org_year UNIQUE (org_id, year)
);

CREATE TABLE audit_annual_item (
  id         BIGSERIAL    PRIMARY KEY,
  org_id     BIGINT       NOT NULL REFERENCES org(id),
  annual_id  BIGINT       NOT NULL REFERENCES audit_annual_plan(id),
  target     VARCHAR(256) NOT NULL,                       -- 审计对象（单位/系统/流程）
  risk_rank  INT          NOT NULL DEFAULT 3,             -- 风险排序 1(最高)–5
  quarter    VARCHAR(2)   NOT NULL DEFAULT 'Q1',          -- 排期 Q1–Q4
  note       TEXT,                                        -- 关注要点/理由
  plan_id    BIGINT,                                      -- 生成的单项审计计划（软引用，可空）
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_annual_item_annual ON audit_annual_item(annual_id);

ALTER TABLE audit_annual_plan ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_audit_annual_plan_iso ON audit_annual_plan
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
ALTER TABLE audit_annual_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_audit_annual_item_iso ON audit_annual_item
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON audit_annual_plan, audit_annual_item TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE audit_annual_plan_id_seq, audit_annual_item_id_seq TO grc_app;

-- 后续审计关联
ALTER TABLE audit_plan ADD COLUMN IF NOT EXISTS follow_up_of BIGINT;   -- 原审计计划（follow-up 验证其整改）
