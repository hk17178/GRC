-- =============================================================
-- V64 · M11 证据深化包（B13 问询/处罚证据联动 + B34 周期性报送）
-- =============================================================

-- 1) B13：证据可挂到 监管问询 / 处罚约谈，闭合这两类监管对象的举证链
--    （与七轮 filing/incident 同范式：问询答复挂回函存证、处罚了结挂整改/缴款凭证）。
ALTER TABLE evidence ADD COLUMN inquiry_id BIGINT;
ALTER TABLE evidence ADD COLUMN penalty_id BIGINT;
CREATE INDEX idx_evidence_inquiry ON evidence(inquiry_id);
CREATE INDEX idx_evidence_penalty ON evidence(penalty_id);

-- 2) B34：周期性报送计划——季/月/年重复的法定报送（如季度反洗钱报表、年度等保报告），
--    到期前由内核到期扫描提醒并自动生成一份 reg_filing 草稿实例，推进 next_due。
CREATE TABLE reg_filing_schedule (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL,                       -- 隔离锚点
  title         VARCHAR(256) NOT NULL,                       -- 报送事项名（生成实例时作为 reg_filing.title 前缀）
  regulator     VARCHAR(64),                                 -- 监管机构
  period        VARCHAR(16)  NOT NULL,                       -- MONTHLY / QUARTERLY / ANNUAL
  lead_days     INT          NOT NULL DEFAULT 15,            -- 提前生成/提醒天数
  next_due      DATE         NOT NULL,                       -- 下次法定报送截止日
  enabled       BOOLEAN      NOT NULL DEFAULT TRUE,          -- 停用后不再生成
  last_generated DATE,                                       -- 最近一次已生成实例对应的到期日（幂等）
  created_by    VARCHAR(64),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_reg_filing_schedule_org ON reg_filing_schedule(org_id);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE reg_filing_schedule ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_reg_filing_schedule_iso ON reg_filing_schedule
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON reg_filing_schedule TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE reg_filing_schedule_id_seq TO grc_app;
