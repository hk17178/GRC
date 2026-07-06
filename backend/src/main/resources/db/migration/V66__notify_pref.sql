-- =============================================================
-- V66 · 通知体验包（B28 订阅偏好）
-- =============================================================
-- notify_preference：每用户的通知订阅偏好——muted_categories 逗号分隔的分类键，
-- 命中分类的通知不进该用户的通知列表。法定时限红线分类由应用层强制不可静音。
-- 以 user_id 为主键（每用户一行）；非业务隔离对象，不携 org_id、不启 RLS（用户维度全局唯一）。
-- =============================================================

CREATE TABLE notify_preference (
  user_id          BIGINT       PRIMARY KEY REFERENCES app_user(id),
  muted_categories TEXT         NOT NULL DEFAULT '',   -- 逗号分隔分类键（REMEDIATION,ASSESSMENT,REGULATION,RISK,AUDIT,FILING）
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE ON notify_preference TO grc_app;
