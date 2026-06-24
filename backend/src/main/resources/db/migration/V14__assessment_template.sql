-- =============================================================
-- V14 · 评估模板库 + 评估项（A2：M2 模板库 + 评估-控件复用）
-- =============================================================
-- 三张表：assessment_template（模板）、assessment_template_item（模板项）、assessment_item（评估项）。
-- 模板项/评估项可引用统一控件库 control_item(id)（control_id 可空，软引用不设 FK 以解耦模块）。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- =============================================================

CREATE TABLE assessment_template (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                              -- 隔离锚点
  code         VARCHAR(32)  NOT NULL,                              -- 模板编码（组织内唯一）
  name         VARCHAR(128) NOT NULL,
  framework    VARCHAR(16)  NOT NULL,                              -- MLPS/ISO27001/PCI_DSS/PBOC
  status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',              -- DRAFT/PUBLISHED/RETIRED
  description  TEXT,
  owner        VARCHAR(64),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_assessment_template_org_code UNIQUE (org_id, code)
);

CREATE TABLE assessment_template_item (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,
  template_id  BIGINT       NOT NULL REFERENCES assessment_template(id),
  seq          INT          NOT NULL,
  control_id   BIGINT,                                             -- 引用统一控件库（软引用，可空）
  clause       VARCHAR(64),
  requirement  TEXT
);
CREATE INDEX idx_assessment_template_item_tpl ON assessment_template_item(template_id);

CREATE TABLE assessment_item (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL,
  assessment_id BIGINT       NOT NULL REFERENCES assessment(id),
  seq           INT          NOT NULL,
  control_id    BIGINT,                                            -- 引用统一控件库（复用，可空）
  clause        VARCHAR(64),
  requirement   TEXT,
  result        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',           -- PENDING/CONFORMING/NONCONFORMING/NOT_APPLICABLE
  conclusion    TEXT,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_assessment_item_assessment ON assessment_item(assessment_id);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE assessment_template ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_assessment_template_iso ON assessment_template
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE assessment_template_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_assessment_template_item_iso ON assessment_template_item
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE assessment_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_assessment_item_iso ON assessment_item
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON assessment_template TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE assessment_template_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON assessment_template_item TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE assessment_template_item_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON assessment_item TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE assessment_item_id_seq TO grc_app;
