-- =============================================================
-- V12 · KRI 关键风险指标监控（A2：M2 风险持续监测）
-- =============================================================
-- 两张表：kri（指标定义 + 最近值/状态）、kri_measurement（测量时序）。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- =============================================================

CREATE TABLE kri (
  id                  BIGSERIAL PRIMARY KEY,
  org_id              BIGINT        NOT NULL,                       -- 隔离锚点
  code                VARCHAR(32)   NOT NULL,                       -- 指标编码（组织内唯一）
  name                VARCHAR(128)  NOT NULL,                       -- 指标名称
  unit                VARCHAR(16),                                  -- 计量单位（可空）
  direction           VARCHAR(16)   NOT NULL,                       -- 阈值方向 UPPER_BAD/LOWER_BAD
  threshold_warning   NUMERIC(18,4) NOT NULL,                       -- 预警阈值
  threshold_critical  NUMERIC(18,4) NOT NULL,                       -- 严重阈值
  current_value       NUMERIC(18,4),                                -- 最近测量值（尚无测量为空）
  current_status      VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN',     -- 最近状态 UNKNOWN/NORMAL/WARNING/CRITICAL
  owner               VARCHAR(64),                                  -- 指标责任人（可空）
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
  CONSTRAINT uk_kri_org_code UNIQUE (org_id, code)
);

CREATE TABLE kri_measurement (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                               -- 隔离锚点（与所属 KRI 同组织）
  kri_id       BIGINT       NOT NULL REFERENCES kri(id),            -- 所属 KRI
  value        NUMERIC(18,4) NOT NULL,                              -- 测量值
  status       VARCHAR(16)  NOT NULL,                               -- 测量当时评定的状态
  measured_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  note         TEXT
);
CREATE INDEX idx_kri_measurement_kri ON kri_measurement(kri_id);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE kri ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_kri_iso ON kri
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE kri_measurement ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_kri_measurement_iso ON kri_measurement
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON kri TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE kri_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON kri_measurement TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE kri_measurement_id_seq TO grc_app;
