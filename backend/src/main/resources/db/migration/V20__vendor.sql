-- =============================================================
-- V20 · 第三方供应商（Phase B：准入/评估/监测）
-- =============================================================
-- vendor（供应商）+ vendor_assessment（供应商风险评估）。
-- 准入门控：启用须先有评估（应用层强制，见 VendorService.activate）。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- =============================================================

CREATE TABLE vendor (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                             -- 隔离锚点
  code         VARCHAR(64)  NOT NULL,                             -- 供应商编号（组织内唯一）
  name         VARCHAR(256) NOT NULL,
  category     VARCHAR(64),                                       -- 服务类别
  contact      VARCHAR(128),                                      -- 联系方式
  status       VARCHAR(16)  NOT NULL DEFAULT 'ONBOARDING',        -- ONBOARDING/ACTIVE/SUSPENDED/TERMINATED
  risk_level   VARCHAR(12),                                       -- 最近评估风险等级（五级，未评估为空）
  criticality  VARCHAR(16),                                       -- 重要性
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_vendor_org_code UNIQUE (org_id, code)
);

CREATE TABLE vendor_assessment (
  id           BIGSERIAL   PRIMARY KEY,
  org_id       BIGINT      NOT NULL,
  vendor_id    BIGINT      NOT NULL REFERENCES vendor(id),
  risk_level   VARCHAR(12) NOT NULL,                              -- 评估风险等级（五级）
  score        INT,                                               -- 评估得分 0–100
  assessor     VARCHAR(64),
  conclusion   TEXT,
  assessed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vendor_assessment_vendor ON vendor_assessment(vendor_id);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE vendor ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_vendor_iso ON vendor
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE vendor_assessment ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_vendor_assessment_iso ON vendor_assessment
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON vendor TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE vendor_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON vendor_assessment TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE vendor_assessment_id_seq TO grc_app;
