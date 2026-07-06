-- =============================================================
-- V63 · AI 回答反馈（AI 深度包 B32）
-- =============================================================
-- ai_feedback：用户对某次 AI 问答的赞/踩 + 可选原因，供检索/生成质量分析。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- 问题/回答只留摘要（前端截断后提交），不留全文，避免长文本与隐私堆积。
-- =============================================================

CREATE TABLE ai_feedback (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL,                       -- 隔离锚点
  question      TEXT,                                        -- 问题摘要
  answer        TEXT,                                        -- 回答摘要
  helpful       BOOLEAN      NOT NULL,                       -- true=赞 / false=踩
  reason        VARCHAR(512),                                -- 踩的原因（可空）
  created_by    VARCHAR(64),                                 -- 反馈人
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_feedback_org ON ai_feedback(org_id, created_at);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE ai_feedback ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_ai_feedback_iso ON ai_feedback
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT ON ai_feedback TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE ai_feedback_id_seq TO grc_app;
