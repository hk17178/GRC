-- =============================================================
-- 安全加固包（评估报告 后续批次·安全加固：B15/B16/B17 的 DDL 部分）：
--  1) B15 登录审计台账 + 失败锁定字段（等保三级测评必查项）
--  2) B17 首登强制改密位（种子口令 demo1234 上线红线的机制化半步）
--  3) B16 留痕/证据保留期限参数（支付机构口径 ≥5 年，系统锁定项）
-- =============================================================

-- 1) 登录审计：成功/失败/锁定拒绝全记录（平台级台账，无 RLS——审计线不受可见域裁剪）
CREATE TABLE login_audit (
  id         BIGSERIAL    PRIMARY KEY,
  username   VARCHAR(64)  NOT NULL,
  success    BOOLEAN      NOT NULL,
  reason     VARCHAR(64),                        -- BAD_CREDENTIAL / LOCKED / PLATFORM_DISABLED / OK
  client_ip  VARCHAR(64),
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_audit_user ON login_audit(username, created_at);
GRANT SELECT, INSERT ON login_audit TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE login_audit_id_seq TO grc_app;

-- 失败锁定：连续失败 5 次锁 15 分钟（阈值代码内常量，后续可参数化）
ALTER TABLE app_user ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN locked_until TIMESTAMPTZ;

-- 2) 首登强制改密位：置 true 的账号登录后必须改密才能继续（生产初始化脚本应对种子账号置 true）
ALTER TABLE app_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- 3) 保留期限参数（集团层锁定项；到期处理策略随归档能力后续交付）
INSERT INTO system_setting (org_id, setting_key, setting_value, value_type, category, description, editable) VALUES
  (1, 'retention.operation-log.years', '5', 'INT', 'compliance',
   '操作留痕（哈希链）最低保留年限——支付机构监管口径不低于 5 年，系统锁定项', FALSE),
  (1, 'retention.evidence.years', '5', 'INT', 'compliance',
   '证据库原件最低保留年限——与留痕同口径，系统锁定项', FALSE);
