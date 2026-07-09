-- =============================================================
-- V79 · B20 控件测试复用（M2 二期）
-- =============================================================
-- 控件有效性测试台账：一个控制项可被反复测试（设计有效性 DESIGN / 运行有效性 OPERATING），
-- 每次测试记结论（EFFECTIVE/DEFICIENT/PARTIAL）+ 有效期（valid_until）。
-- 复用语义：新的审计/评估要依赖某控件时，若库内已有该控件"有效且未过期"的 EFFECTIVE 测试，
--   可直接复用其结论而不必重测（reusableTest 返回该记录）——省重复测试成本、保口径一致。
-- 携 org_id + RLS，按组织隔离。
-- =============================================================

CREATE TABLE control_test (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL,                  -- 隔离锚点（= 控件所属组织）
  control_id  BIGINT       NOT NULL REFERENCES control_item(id),
  test_type   VARCHAR(16)  NOT NULL,                  -- DESIGN（设计有效性）/ OPERATING（运行有效性）
  result      VARCHAR(16)  NOT NULL,                  -- EFFECTIVE / DEFICIENT / PARTIAL
  tested_by   VARCHAR(64),
  tested_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  valid_until DATE,                                   -- 结论有效期（复用窗口上界，NULL=不限但不参与复用）
  note        TEXT,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_control_test_control ON control_test(org_id, control_id, id DESC);

ALTER TABLE control_test ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_control_test_iso ON control_test
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT ON control_test TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE control_test_id_seq TO grc_app;
