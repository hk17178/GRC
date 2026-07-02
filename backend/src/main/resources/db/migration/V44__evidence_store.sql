-- =============================================================
-- V44 · 证据库（M3 深度：证据管理 / 卷宗导出 / 反向取证）
-- 证据文件（bytea）挂到 审计计划 / 审计发现 / 整改单 任一对象上；
-- sha256 指纹在上传时计算固化——反向取证时校验文件是否被篡改并回溯关联对象。
-- =============================================================

CREATE TABLE evidence (
  id             BIGSERIAL    PRIMARY KEY,
  org_id         BIGINT       NOT NULL REFERENCES org(id),        -- 隔离锚点
  plan_id        BIGINT       REFERENCES audit_plan(id),          -- 关联审计计划（可空）
  finding_id     BIGINT       REFERENCES audit_finding(id),       -- 关联审计发现（可空）
  remediation_id BIGINT       REFERENCES remediation_order(id),   -- 关联整改单（可空）
  name           VARCHAR(256) NOT NULL,                           -- 证据名称/说明
  file_name      VARCHAR(256),                                    -- 原始文件名
  content_type   VARCHAR(128),                                    -- MIME 类型
  data           BYTEA        NOT NULL,                           -- 文件内容
  sha256         VARCHAR(64)  NOT NULL,                           -- 上传时固化的指纹（防篡改）
  uploaded_by    VARCHAR(64),
  uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_evidence_org ON evidence(org_id);
CREATE INDEX idx_evidence_finding ON evidence(finding_id);
CREATE INDEX idx_evidence_remediation ON evidence(remediation_id);

ALTER TABLE evidence ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_evidence_iso ON evidence
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON evidence TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE evidence_id_seq TO grc_app;
