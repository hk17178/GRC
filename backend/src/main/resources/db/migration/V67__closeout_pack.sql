-- =============================================================
-- V67 · 收口批一期（B25 爬虫失效计数 + B24 证书有效期台账）
-- =============================================================

-- 1) B25：采集源连续未抓到计数——失败或零命中累加，达阈值产 CRAWLER_FAILED 告警运维。
ALTER TABLE regulation_source ADD COLUMN consecutive_miss INT NOT NULL DEFAULT 0;

-- 2) B24：证书有效期台账（M3-13）——外审页「认证有效期临近」的真值来源，
--    到期由内核到期扫描提醒（60/30/7 天）。携 org_id 隔离锚点，RLS 裁剪。
CREATE TABLE certificate (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                       -- 隔离锚点
  name         VARCHAR(256) NOT NULL,                       -- 证书名称
  framework    VARCHAR(32),                                 -- 认证体系（ISO27001/MLPS/PCI_DSS/PIMS…）
  cert_no      VARCHAR(128),                                -- 证书编号
  issuer       VARCHAR(128),                                -- 发证机构
  issued_date  DATE,                                        -- 发证日期
  expiry_date  DATE         NOT NULL,                       -- 到期日期（到期扫描锚点）
  status       VARCHAR(16)  NOT NULL DEFAULT 'VALID',       -- VALID/EXPIRED/REVOKED
  created_by   VARCHAR(64),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_certificate_org ON certificate(org_id, expiry_date);

ALTER TABLE certificate ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_certificate_iso ON certificate
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE ON certificate TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE certificate_id_seq TO grc_app;
