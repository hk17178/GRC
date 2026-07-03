-- =============================================================
-- V57 · 手机扫码手写签名（UAT 四轮 #5）
-- 一次性签名令牌：桌面签批时生成 → 手机扫码打开免登录签名页（凭 token）→
-- 提交签名 → 桌面轮询取回。token 即凭证：随机 UUID、5 分钟过期、取回即作废。
-- 平台表不挂 RLS（免登录页无可见域）；不冗余业务数据，仅评估标题供手机端确认。
-- =============================================================
CREATE TABLE signature_ticket (
  id            BIGSERIAL    PRIMARY KEY,
  token         VARCHAR(64)  NOT NULL UNIQUE,
  assessment_id BIGINT       NOT NULL,
  org_id        BIGINT       NOT NULL,
  title         VARCHAR(256),                             -- 评估标题（手机端确认用，创建时冗余）
  status        VARCHAR(12)  NOT NULL DEFAULT 'PENDING',  -- PENDING/SIGNED/USED/EXPIRED
  signature     BYTEA,                                    -- 手机提交的签名 PNG
  created_by    VARCHAR(64),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_signature_ticket_token ON signature_ticket(token);

GRANT SELECT, INSERT, UPDATE, DELETE ON signature_ticket TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE signature_ticket_id_seq TO grc_app;
