-- =============================================================
-- V38 · M2 周边（对照评估 P2）：处置决策四选一 + 评估计划管理
-- =============================================================
-- 1) risk_finding 加处置决策枚举列（需求 4.5.3：降低/接受/转移/规避 四选一，与 treatment_plan 文本并存）。
-- 2) assessment_plan：评估计划管理（需求 4.2.1：年度/季度周期性计划与临时专项，创建/排期/状态/启动生成评估）。
-- =============================================================

ALTER TABLE risk_finding ADD COLUMN IF NOT EXISTS treatment_decision VARCHAR(16); -- MITIGATE/ACCEPT/TRANSFER/AVOID

CREATE TABLE assessment_plan (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL,
  title         VARCHAR(256) NOT NULL,               -- 计划主题（如 2026 年度等保自评）
  period_type   VARCHAR(16)  NOT NULL DEFAULT 'ANNUAL', -- ANNUAL/QUARTERLY/ADHOC(临时专项)
  planned_date  DATE,                                -- 计划开始日期
  template_id   BIGINT,                              -- 关联评估模板（启动时带入，可空）
  status        VARCHAR(16)  NOT NULL DEFAULT 'PLANNED', -- PLANNED/STARTED/DONE
  assessment_id BIGINT,                              -- 启动后生成的评估 id
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
ALTER TABLE assessment_plan ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_assessment_plan_iso ON assessment_plan
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON assessment_plan TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE assessment_plan_id_seq TO grc_app;
