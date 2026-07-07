-- =============================================================
-- V69 · B12 低代码 Phase1（D1-8 H-04 自定义字段）
-- =============================================================
-- 客户不改代码即可为宿主对象登记自定义字段：元数据登记在 custom_field_def，
-- 字段值落宿主表统一的 ext JSONB 扩展列（键名与登记一致）。
-- 第一性红线（D1-8 §一）：绝不绕过组织隔离——def 携 org_id + RLS；ext 值随宿主行的
-- org_id 隔离（宿主表本就有 RLS）。本期试点宿主 = asset(M6)。
-- =============================================================

-- 1) 自定义字段定义（每 org × object_type × field_key 唯一）
CREATE TABLE custom_field_def (
  id             BIGSERIAL    PRIMARY KEY,
  org_id         BIGINT       NOT NULL,                       -- 隔离锚点
  object_type    VARCHAR(32)  NOT NULL,                       -- 宿主对象类型（本期 'ASSET'）
  field_key      VARCHAR(64)  NOT NULL,                       -- 字段键（ext JSON 键名，同 org+object 内唯一）
  label          VARCHAR(128) NOT NULL,                       -- 显示标签
  data_type      VARCHAR(16)  NOT NULL,                       -- TEXT/NUMBER/DATE/BOOL/SELECT
  options        TEXT,                                        -- SELECT 选项（分号分隔）
  required       BOOLEAN      NOT NULL DEFAULT FALSE,         -- 必填
  is_sensitive   BOOLEAN      NOT NULL DEFAULT FALSE,         -- 敏感（导出/问答按级别脱敏，后续 phase）
  is_aggregatable BOOLEAN     NOT NULL DEFAULT FALSE,         -- 可聚合（供 M9 看板/KPI，后续 phase）
  seq            INT          NOT NULL DEFAULT 0,             -- 展示顺序
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE/RETIRED
  created_by     VARCHAR(64),
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_custom_field UNIQUE (org_id, object_type, field_key)
);
CREATE INDEX idx_custom_field_obj ON custom_field_def(org_id, object_type, status);

ALTER TABLE custom_field_def ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_custom_field_iso ON custom_field_def
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE ON custom_field_def TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE custom_field_def_id_seq TO grc_app;

-- 2) 宿主表扩展列（试点：asset）。ext 键 = field_key，值随宿主行 RLS 隔离。
ALTER TABLE asset ADD COLUMN ext JSONB NOT NULL DEFAULT '{}'::jsonb;
