-- =============================================================
-- V50 · 内部审计 A2：审计通知书 + 审计程序/工作底稿（IIA 现场实施要件）
-- =============================================================
-- ① 审计通知书：立项后向被审计单位签发（被审计单位/范围/依据/审计组/签发人与时间）
ALTER TABLE audit_plan ADD COLUMN IF NOT EXISTS auditee          VARCHAR(128);  -- 被审计单位/部门
ALTER TABLE audit_plan ADD COLUMN IF NOT EXISTS notice_scope     TEXT;          -- 审计范围（通知书）
ALTER TABLE audit_plan ADD COLUMN IF NOT EXISTS notice_basis     TEXT;          -- 审计依据（制度/年度计划/监管要求）
ALTER TABLE audit_plan ADD COLUMN IF NOT EXISTS audit_team       VARCHAR(256);  -- 审计组成员（组长在前）
ALTER TABLE audit_plan ADD COLUMN IF NOT EXISTS notice_issued_by VARCHAR(64);   -- 通知书签发人
ALTER TABLE audit_plan ADD COLUMN IF NOT EXISTS notice_issued_at TIMESTAMPTZ;   -- 通知书签发时间

-- ② 审计程序 / 工作底稿：程序步骤 → 执行记录（底稿）→ 复核
CREATE TABLE audit_procedure (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL REFERENCES org(id),        -- 隔离锚点
  plan_id       BIGINT       NOT NULL REFERENCES audit_plan(id), -- 所属审计计划
  seq           INT          NOT NULL,                           -- 程序序号（计划内递增）
  name          VARCHAR(256) NOT NULL,                           -- 程序步骤（做什么）
  objective     TEXT,                                            -- 程序目标（验证什么）
  workpaper_no  VARCHAR(32)  NOT NULL,                           -- 底稿编号 WP-{plan}-{seq}
  executor      VARCHAR(64),                                     -- 执行人
  executed_at   TIMESTAMPTZ,                                     -- 执行时间
  result        TEXT,                                            -- 执行记录（工作底稿正文）
  status        VARCHAR(12)  NOT NULL DEFAULT 'PENDING',         -- PENDING/DONE/REVIEWED
  reviewer      VARCHAR(64),                                     -- 复核人
  reviewed_at   TIMESTAMPTZ,                                     -- 复核时间
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_procedure_plan ON audit_procedure(plan_id);

ALTER TABLE audit_procedure ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_audit_procedure_iso ON audit_procedure
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON audit_procedure TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE audit_procedure_id_seq TO grc_app;
