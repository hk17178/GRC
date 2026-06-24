-- =============================================================
-- V15 · A-T-V 资产-威胁-脆弱风险识别（A2 收尾，桥接 M6 资产）
-- =============================================================
-- 三张表：threat（威胁库）、vulnerability（脆弱性库）、risk_scenario（A-T-V 风险场景）。
-- risk_scenario.asset_id 软引用 M6 asset（跨模块解耦不设 FK，由 Service 校验资产可见）。
-- 固有等级 inherent_level 由可能性×影响经风险矩阵在应用层派生为平台五级后落库。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- =============================================================

CREATE TABLE threat (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,
  code         VARCHAR(32)  NOT NULL,
  name         VARCHAR(128) NOT NULL,
  category     VARCHAR(64),
  description  TEXT,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_threat_org_code UNIQUE (org_id, code)
);

CREATE TABLE vulnerability (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,
  code         VARCHAR(32)  NOT NULL,
  name         VARCHAR(128) NOT NULL,
  category     VARCHAR(64),
  description  TEXT,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_vulnerability_org_code UNIQUE (org_id, code)
);

CREATE TABLE risk_scenario (
  id                BIGSERIAL   PRIMARY KEY,
  org_id            BIGINT      NOT NULL,
  asset_id          BIGINT      NOT NULL,                          -- 软引用 M6 asset
  threat_id         BIGINT      NOT NULL REFERENCES threat(id),
  vulnerability_id  BIGINT      NOT NULL REFERENCES vulnerability(id),
  likelihood        INT         NOT NULL,                          -- 可能性 1–5
  impact            INT         NOT NULL,                          -- 影响 1–5
  inherent_level    VARCHAR(12) NOT NULL,                          -- 派生五级 VERY_LOW..VERY_HIGH
  description       TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_scenario_atv UNIQUE (asset_id, threat_id, vulnerability_id)
);
CREATE INDEX idx_risk_scenario_asset ON risk_scenario(asset_id);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE threat ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_threat_iso ON threat
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE vulnerability ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_vulnerability_iso ON vulnerability
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE risk_scenario ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_risk_scenario_iso ON risk_scenario
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON threat TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE threat_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON vulnerability TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE vulnerability_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON risk_scenario TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE risk_scenario_id_seq TO grc_app;
