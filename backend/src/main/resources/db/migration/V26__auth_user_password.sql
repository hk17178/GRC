-- =============================================================
-- V26 · 认证地基（增强③ R1：app_user 加口令）
-- =============================================================
-- 为登录认证给 app_user 增加 BCrypt 口令哈希 + 显示名 + 启用位。
-- 种子用户用 pgcrypto 的 bcrypt(gen_salt('bf')) 生成 $2a$ 哈希（Spring BCryptPasswordEncoder 可校验）。
-- 开发口令统一 demo1234（上线必须改）；我不经手任何真实明文口令。
-- app_user 不挂 RLS（计算可见域需读取），grc_app 已有 SELECT 权。
-- =============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_hash VARCHAR(72);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS display_name  VARCHAR(64);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS enabled       BOOLEAN NOT NULL DEFAULT true;

-- 种子用户口令（demo1234）+ 显示名
UPDATE app_user SET password_hash = crypt('demo1234', gen_salt('bf', 10)),
                    display_name = CASE username
                        WHEN 'group_admin' THEN '集团管理员'
                        WHEN 'pay_user'    THEN '支付科技用户'
                        WHEN 'cf_user'     THEN '消费金融用户'
                        ELSE username END
WHERE password_hash IS NULL;

-- 运行期账号读 app_user 口令以登录（若尚未授予）
GRANT SELECT ON app_user TO grc_app;
