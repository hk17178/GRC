-- =============================================================
-- V16 · 整改工单（A3：M3 审计发现的整改→验证闭环）
-- =============================================================
-- remediation_order：审计发现的整改任务。与业务表同口径：携 org_id、ENABLE RLS。
-- 验证闭环红线：发现须有 ≥1 条 VERIFIED 工单方可标记已整改（由 AuditFindingService.remediate 强制）。
-- =============================================================

CREATE TABLE remediation_order (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL,                               -- 隔离锚点（与发现同组织）
  finding_id  BIGINT       NOT NULL REFERENCES audit_finding(id),  -- 所属审计发现
  assignee    VARCHAR(64),                                         -- 整改责任人
  due_date    DATE,                                                -- 整改期限
  measure     TEXT,                                                -- 整改措施
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',             -- PENDING/IN_PROGRESS/SUBMITTED/VERIFIED
  evidence    TEXT,                                                -- 整改证据（提交时）
  verifier    VARCHAR(64),                                         -- 验证人（验证通过时）
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_remediation_order_finding ON remediation_order(finding_id);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE remediation_order ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_remediation_order_iso ON remediation_order
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON remediation_order TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE remediation_order_id_seq TO grc_app;
