-- =============================================================
-- V18 · 年度合规计划（A5：M11 监管事项·年度计划）
-- =============================================================
-- compliance_plan（年度计划）+ compliance_plan_item（计划项）。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- =============================================================

CREATE TABLE compliance_plan (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL,                               -- 隔离锚点
  year        INT          NOT NULL,                               -- 计划年度
  title       VARCHAR(128) NOT NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',               -- DRAFT/ACTIVE/CLOSED
  owner       VARCHAR(64),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_compliance_plan_org_year UNIQUE (org_id, year)
);

CREATE TABLE compliance_plan_item (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL,
  plan_id     BIGINT       NOT NULL REFERENCES compliance_plan(id),
  seq         INT          NOT NULL,
  matter      TEXT,                                                -- 合规事项
  owner_dept  VARCHAR(64),                                         -- 责任部门
  due_date    DATE,                                                -- 计划完成时间
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',             -- PENDING/IN_PROGRESS/DONE
  note        TEXT,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_compliance_plan_item_plan ON compliance_plan_item(plan_id);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE compliance_plan ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_compliance_plan_iso ON compliance_plan
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE compliance_plan_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_compliance_plan_item_iso ON compliance_plan_item
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON compliance_plan TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE compliance_plan_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON compliance_plan_item TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE compliance_plan_item_id_seq TO grc_app;
