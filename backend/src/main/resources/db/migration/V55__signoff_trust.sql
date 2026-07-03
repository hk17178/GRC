-- =============================================================
-- V55 · 管理层签批可信度（UAT 三轮 #4）
-- 双因素存证：签批须重输登录密码（身份再认证，后端 BCrypt 校验）+ 手写签名（canvas）。
-- 签名 PNG 原图落库、sha256 指纹固化并写入哈希链——事后可校验签名图未被篡改。
-- =============================================================
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS mgmt_signature        BYTEA;        -- 手写签名 PNG
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS mgmt_signature_sha256 VARCHAR(64);  -- 签名指纹（哈希链锚点）
