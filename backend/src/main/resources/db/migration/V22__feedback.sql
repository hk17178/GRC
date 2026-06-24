-- =============================================================
-- V22 · 建议与反馈（Phase B / CR-004：反馈管理）
-- =============================================================
-- feedback：一条建议/投诉/缺陷/咨询，含处理状态机与处置结果。
-- 办结闭环：RESOLVED 须填处置结果（应用层强制，见 FeedbackService.resolve）。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- =============================================================

CREATE TABLE feedback (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL,                              -- 隔离锚点
  type        VARCHAR(16)  NOT NULL,                              -- SUGGESTION/COMPLAINT/BUG/QUESTION
  title       VARCHAR(256) NOT NULL,
  content     TEXT,
  submitter   VARCHAR(64),
  status      VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTED',          -- SUBMITTED/IN_PROGRESS/RESOLVED/CLOSED/REJECTED
  handler     VARCHAR(64),                                        -- 处理人（受理时分派）
  resolution  TEXT,                                               -- 处置结果（办结必填）
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_feedback_status ON feedback(org_id, status);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE feedback ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_feedback_iso ON feedback
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON feedback TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE feedback_id_seq TO grc_app;
