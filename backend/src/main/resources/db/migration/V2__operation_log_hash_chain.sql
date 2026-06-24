-- =====================================================================
-- V2 · 操作日志防篡改哈希链（一期横向红线）
-- 设计依据：D1-3 §8（ADR-C 防篡改=哈希链）、D1-2 §13、D1-9 H-02。
-- 要点：
--   1) 每条日志 curr_hash = SHA256(规范化内容 + 前一条 curr_hash)，链式串联；
--   2) 按 org_id 分链（各子公司独立链，互不串扰、可独立验证）；
--   3) 仅追加：应用账号 grc_app 仅有 INSERT/SELECT，无 UPDATE/DELETE（应用层不可改）；
--   4) 篡改检测靠"重算链"——即便有人直连数据库（绕过应用）改了某行，重算即发现；
--   5) trusted_timestamp 周期性对链尖加盖可信时间戳（TSA，RFC3161；外部对接留待联测阶段）。
-- =====================================================================

CREATE TABLE operation_log (
  id             BIGSERIAL PRIMARY KEY,
  org_id         BIGINT NOT NULL REFERENCES org(id),     -- 隔离锚点 + 分链键
  seq            BIGINT NOT NULL,                         -- 该 org 链内序号，从 1 起，连续
  action         VARCHAR(64)  NOT NULL,                   -- 操作类型（LOGIN/UPDATE_POLICY/...）
  actor          VARCHAR(64)  NOT NULL,                   -- 操作主体
  entity         VARCHAR(128),                            -- 操作对象（表:主键 等）
  detail         TEXT,                                    -- 详情（JSON/文本）
  created_at_ms  BIGINT       NOT NULL,                   -- 入链时间(epoch毫秒)，纳入哈希（确定性）
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),     -- 展示用，不纳入哈希
  prev_hash      VARCHAR(64)  NOT NULL,                   -- 前一条 curr_hash（链首用 GENESIS 全 0 种子）
  curr_hash      VARCHAR(64)  NOT NULL,                   -- = SHA256(规范化内容 + prev_hash)
  UNIQUE (org_id, seq)                                    -- 同链序号唯一，杜绝并发重号
);
CREATE INDEX idx_oplog_org_seq ON operation_log(org_id, seq);

-- ---------- 组织隔离：与业务表同口径，按 visible_orgs 裁剪（含写入校验） ----------
ALTER TABLE operation_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_oplog_iso ON operation_log
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 仅追加授权：grc_app 无 UPDATE/DELETE（应用层不可篡改） ----------
GRANT SELECT, INSERT ON operation_log TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE operation_log_id_seq TO grc_app;

-- ---------- 可信时间戳锚定（周期性对链尖加盖 TSA 时间戳） ----------
CREATE TABLE trusted_timestamp (
  id          BIGSERIAL PRIMARY KEY,
  org_id      BIGINT NOT NULL REFERENCES org(id),
  chain_seq   BIGINT NOT NULL,                            -- 锚定到 operation_log 的该 seq（检查点）
  anchor_hash VARCHAR(64) NOT NULL,                       -- 被加盖时间戳的链尖 curr_hash
  tsa_token   BYTEA,                                      -- TSA 返回的 RFC3161 令牌（外部对接，联测阶段接入）
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tsa_org ON trusted_timestamp(org_id, chain_seq);
GRANT SELECT, INSERT ON trusted_timestamp TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE trusted_timestamp_id_seq TO grc_app;
