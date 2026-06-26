-- =============================================================
-- V34 · 登录页与品牌配置（系统设置 → 平台标识，补回 UAT 反馈缺失项）
-- =============================================================
-- 登录页在【认证前】渲染，品牌/文案需对所有用户全局生效，故用平台级单行全局表（无 org_id、无 RLS），
-- 提供公开读端点（GET /api/branding，免登录）+ 受控写端点（PUT，门控 "settings"）。
-- 字段可空：为空时前端回退到 i18n 默认文案。
-- =============================================================

CREATE TABLE branding_config (
  id               BIGINT       PRIMARY KEY,
  brand_name       TEXT,                       -- 平台名称（品牌侧大字）
  brand_sub        TEXT,                       -- 平台副名
  logo_text        VARCHAR(8),                 -- Logo 字符（无图时显示，如 "G"）
  logo_img         TEXT,                       -- Logo 图片（URL 或 data URI；优先于字符）
  login_title_zh   TEXT,                       -- 登录页主标题（中，\n 换行）
  login_title_en   TEXT,
  login_slogan_zh  TEXT,                       -- 登录页标语（中）
  login_slogan_en  TEXT,
  forgot_url       TEXT,                       -- 忘记密码链接
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by       VARCHAR(64),
  CONSTRAINT ck_branding_singleton CHECK (id = 1)
);

INSERT INTO branding_config (id) VALUES (1);

GRANT SELECT, INSERT, UPDATE ON branding_config TO grc_app;
