-- =============================================================
-- V48 · 风险评估 R2：风险登记册整合（识别→分析→评价打通）
-- ① risk_finding 加来源场景：A-T-V 场景一键生成风险发现，识别与登记不再断链；
-- ② assessment_asset：评估范围资产清单（背景建立的"范围"落到具体资产，GB/T 20984 资产识别）。
-- =============================================================
ALTER TABLE risk_finding ADD COLUMN IF NOT EXISTS scenario_id BIGINT;   -- 来源 A-T-V 场景（软引用，可空）

CREATE TABLE assessment_asset (
  id            BIGSERIAL PRIMARY KEY,
  org_id        BIGINT    NOT NULL REFERENCES org(id),          -- 隔离锚点
  assessment_id BIGINT    NOT NULL REFERENCES assessment(id),   -- 所属评估
  asset_id      BIGINT    NOT NULL,                             -- 软引用 M6 asset（Service 校验可见）
  added_by      VARCHAR(64),
  added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_assessment_asset UNIQUE (assessment_id, asset_id)
);
CREATE INDEX idx_assessment_asset_assessment ON assessment_asset(assessment_id);

ALTER TABLE assessment_asset ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_assessment_asset_iso ON assessment_asset
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON assessment_asset TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE assessment_asset_id_seq TO grc_app;
