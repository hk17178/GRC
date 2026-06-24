-- =============================================================
-- V19 · 法规跟踪（Phase B：法规库 + 变更动态 + 影响评估闭环）
-- =============================================================
-- regulation（法规库）+ regulation_change（法规变更/动态，含影响评估）。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- =============================================================

CREATE TABLE regulation (
  id              BIGSERIAL    PRIMARY KEY,
  org_id          BIGINT       NOT NULL,                          -- 隔离锚点
  code            VARCHAR(64)  NOT NULL,                          -- 法规编号（组织内唯一）
  title           VARCHAR(256) NOT NULL,
  issuer          VARCHAR(64),                                    -- 发布机构
  category        VARCHAR(64),                                    -- 分类
  status          VARCHAR(16)  NOT NULL DEFAULT 'TRACKING',       -- TRACKING/EFFECTIVE/SUPERSEDED/ABOLISHED
  effective_date  DATE,
  summary         TEXT,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_regulation_org_code UNIQUE (org_id, code)
);

CREATE TABLE regulation_change (
  id             BIGSERIAL   PRIMARY KEY,
  org_id         BIGINT      NOT NULL,
  regulation_id  BIGINT      NOT NULL REFERENCES regulation(id),
  change_type    VARCHAR(16) NOT NULL,                            -- ENACTED/AMENDED/ABOLISHED
  change_date    DATE,
  description    TEXT,
  impact_status  VARCHAR(16) NOT NULL DEFAULT 'PENDING',          -- PENDING/ASSESSED
  impact_scope   TEXT,                                            -- 受影响范围（评估时）
  impact_note    TEXT,                                            -- 处置说明（评估时）
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_regulation_change_reg ON regulation_change(regulation_id);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE regulation ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_regulation_iso ON regulation
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE regulation_change ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_regulation_change_iso ON regulation_change
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON regulation TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE regulation_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON regulation_change TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE regulation_change_id_seq TO grc_app;
