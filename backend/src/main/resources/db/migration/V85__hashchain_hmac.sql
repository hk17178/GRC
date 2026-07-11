-- =============================================================
-- V85 · 哈希链完整性加固（安全评审 H-1 / M-4）
-- =============================================================
-- H-1：原为无密钥裸 SHA-256，任何能直连库写入者可自行重算整条链 → 改 keyed-HMAC-SHA256
--      （密钥 GRC_HASHCHAIN_SECRET 仅环境注入）。为兼容历史数据，按行记算法：
--      历史行 hash_algo='SHA256'（沿用旧校验），新行 hash_algo='HMAC-SHA256'（keyed）。
-- M-4：verify 无法检测"链尾被截断"。增每 org 链尖锚定表，记 max_seq/tip_hash 及对其的 HMAC 签名；
--      直连库删除链尾若干行者无密钥无法同步伪造 anchor_hmac，verify 比对即发现截断。
-- =============================================================

-- 1) 逐行算法标记（默认 SHA256 兼容历史；新追加写 HMAC-SHA256）
ALTER TABLE operation_log ADD COLUMN hash_algo VARCHAR(16) NOT NULL DEFAULT 'SHA256';

-- 2) 链尖锚定（每 org 一行，随每次追加 upsert）
CREATE TABLE operation_log_anchor (
  org_id      BIGINT       PRIMARY KEY,
  max_seq     BIGINT       NOT NULL,
  tip_hash    VARCHAR(64)  NOT NULL,
  anchor_hmac VARCHAR(64)  NOT NULL,               -- HMAC(key, org|max_seq|tip_hash)
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
ALTER TABLE operation_log_anchor ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_operation_log_anchor_iso ON operation_log_anchor
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON operation_log_anchor TO grc_app;
