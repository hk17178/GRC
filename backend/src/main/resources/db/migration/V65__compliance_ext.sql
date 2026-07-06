-- =============================================================
-- V65 · 合规扩展包（B14 ROPA 法定字段 + B29 出站哈希审计）
-- =============================================================

-- 1) B14：ROPA 补个保法§55/56 法定字段——处理方式、接收方。
ALTER TABLE ropa ADD COLUMN processing_method VARCHAR(256);
ALTER TABLE ropa ADD COLUMN recipients        TEXT;

-- 2) B29：出站回复审批通过时固化出站内容的 sha256——记录"实际对外发出的确切内容"，
--    事后可校验是否被篡改（红线：出站内容哈希审计）。
ALTER TABLE feedback ADD COLUMN outbound_sha256 VARCHAR(64);
