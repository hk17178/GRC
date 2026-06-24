-- =============================================================
-- V13 · 统一控件库（A2：M2 统一控件库，一控多框架复用）
-- =============================================================
-- 两张表：control_item（控制项）、control_framework_ref（控制项→框架条款映射）。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- 注：表名用 control_item（control 为 SQL 关键字，避免歧义）。
-- =============================================================

CREATE TABLE control_item (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                              -- 隔离锚点
  code         VARCHAR(32)  NOT NULL,                              -- 控制项编码（组织内唯一）
  name         VARCHAR(128) NOT NULL,                              -- 名称
  description  TEXT,                                               -- 描述/要求
  domain       VARCHAR(64),                                        -- 控制域/分类
  status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',             -- ACTIVE/RETIRED
  owner        VARCHAR(64),                                        -- 责任人（可空）
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_control_org_code UNIQUE (org_id, code)
);

CREATE TABLE control_framework_ref (
  id          BIGSERIAL   PRIMARY KEY,
  org_id      BIGINT      NOT NULL,                                -- 隔离锚点（与控制项同组织）
  control_id  BIGINT      NOT NULL REFERENCES control_item(id),    -- 所属控制项
  framework   VARCHAR(16) NOT NULL,                                -- MLPS/ISO27001/PCI_DSS/PBOC
  clause      VARCHAR(64) NOT NULL,                                -- 框架内条款编号
  CONSTRAINT uk_control_framework_clause UNIQUE (control_id, framework, clause)
);
CREATE INDEX idx_control_framework_ref_control ON control_framework_ref(control_id);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE control_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_control_item_iso ON control_item
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE control_framework_ref ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_control_framework_ref_iso ON control_framework_ref
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON control_item TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE control_item_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON control_framework_ref TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE control_framework_ref_id_seq TO grc_app;
