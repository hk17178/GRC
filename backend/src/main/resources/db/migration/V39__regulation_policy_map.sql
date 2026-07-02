-- =============================================================
-- V39 · M4 深度（对照评估 P2）：法规-制度映射 + AI 变更摘要
-- =============================================================
-- 1) regulation_policy_map：法规条款 ↔ 制度 映射（需求 6.2 右栏"法规-制度映射概览"）。
-- 2) regulation_change 加 ai_summary：变更的 AI 条款级摘要（需求 6.5.1；本地离线模式诚实标注）。
-- =============================================================

CREATE TABLE regulation_policy_map (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL,
  regulation_id BIGINT       NOT NULL,            -- 法规
  policy_id     BIGINT       NOT NULL,            -- 命中的制度
  clause        VARCHAR(128),                     -- 法规条款（如 §41）
  note          VARCHAR(256),                     -- 映射说明（如 对应制度第3章日志留存）
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_reg_policy_map UNIQUE (regulation_id, policy_id, clause)
);
ALTER TABLE regulation_policy_map ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_reg_policy_map_iso ON regulation_policy_map
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, DELETE ON regulation_policy_map TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE regulation_policy_map_id_seq TO grc_app;

ALTER TABLE regulation_change ADD COLUMN IF NOT EXISTS ai_summary TEXT;  -- AI 条款级变更摘要
