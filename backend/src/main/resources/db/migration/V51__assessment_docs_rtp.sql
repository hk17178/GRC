-- =============================================================
-- V51 · 风险评估 R3：过程文档中心 + 风险处置计划（RTP）实体化
-- 过程文档（GB/T 20984 附录）：评估计划书/访谈记录等人工上传件 + 系统生成件登记，
-- sha256 固化防篡改（与证据库同范式）。
-- RTP（ISO 27001 必备）：每条风险发现一份处置计划——措施/责任人/期限/资源/预期残余。
-- =============================================================

CREATE TABLE assessment_doc (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL REFERENCES org(id),          -- 隔离锚点
  assessment_id BIGINT       NOT NULL REFERENCES assessment(id),   -- 所属评估
  doc_type      VARCHAR(16)  NOT NULL,                             -- PLAN 计划书/INTERVIEW 访谈记录/REPORT 报告/RTP 处置计划/ACCEPTANCE 接受声明/OTHER
  name          VARCHAR(256) NOT NULL,                             -- 文档名称/说明
  file_name     VARCHAR(256),
  content_type  VARCHAR(128),
  data          BYTEA,                                             -- 文件内容（SYSTEM 登记件可空）
  source        VARCHAR(8)   NOT NULL DEFAULT 'UPLOAD',            -- UPLOAD 人工上传 / SYSTEM 系统生成
  sha256        VARCHAR(64),                                       -- 指纹（有文件时固化）
  uploaded_by   VARCHAR(64),
  uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_assessment_doc_assessment ON assessment_doc(assessment_id);

ALTER TABLE assessment_doc ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_assessment_doc_iso ON assessment_doc
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON assessment_doc TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE assessment_doc_id_seq TO grc_app;

CREATE TABLE risk_treatment (
  id                BIGSERIAL   PRIMARY KEY,
  org_id            BIGINT      NOT NULL REFERENCES org(id),           -- 隔离锚点
  finding_id        BIGINT      NOT NULL REFERENCES risk_finding(id),  -- 一发现一计划
  measure           TEXT,                                              -- 处置措施
  owner             VARCHAR(64),                                       -- 责任人
  due_date          DATE,                                              -- 完成期限
  resource          TEXT,                                              -- 所需资源/预算
  expected_residual VARCHAR(12),                                       -- 预期残余等级（五级）
  status            VARCHAR(16) NOT NULL DEFAULT 'PENDING',            -- PENDING/IN_PROGRESS/DONE
  updated_by        VARCHAR(64),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_risk_treatment_finding UNIQUE (finding_id)
);

ALTER TABLE risk_treatment ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_risk_treatment_iso ON risk_treatment
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON risk_treatment TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE risk_treatment_id_seq TO grc_app;
