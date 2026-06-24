-- =============================================================
-- V23 · 系统设置（Phase B：租户配置 / D1-8 可配置性）
-- =============================================================
-- system_setting：键值配置项（按 org 分租户）。editable=false 为系统锁定项不可改（应用层强制）。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- =============================================================

CREATE TABLE system_setting (
  id             BIGSERIAL    PRIMARY KEY,
  org_id         BIGINT       NOT NULL,                           -- 隔离锚点（租户）
  setting_key    VARCHAR(128) NOT NULL,                           -- 配置键（组织内唯一）
  setting_value  TEXT,                                            -- 取值（按 value_type 解释）
  value_type     VARCHAR(16)  NOT NULL,                           -- STRING/INT/BOOL/JSON
  category       VARCHAR(64),                                     -- 分组
  description    TEXT,
  editable       BOOLEAN      NOT NULL DEFAULT true,              -- false=系统锁定项不可改
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_system_setting_org_key UNIQUE (org_id, setting_key)
);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE system_setting ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_system_setting_iso ON system_setting
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON system_setting TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE system_setting_id_seq TO grc_app;
