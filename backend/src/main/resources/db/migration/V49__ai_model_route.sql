-- =============================================================
-- V49 · 模型分配：按场景路由（问答/材料生成/变更摘要/制度匹配 各配各的模型）
-- 平台级（同 V33 ai_provider_config），无 org_id 不挂 RLS；写门控 "ai"。
-- 场景：QA 智能问答 / MATERIAL 材料生成 / REG_SUMMARY 法规变更摘要 / POLICY_MAP 法规-制度匹配建议。
-- 未配置或停用的场景回退全局配置（ai_provider_config），全局仍是兜底。
-- 密钥 AES 加密（GRC_CONFIG_SECRET），接口只回显末位掩码。
-- =============================================================

CREATE TABLE ai_model_route (
  id          BIGSERIAL    PRIMARY KEY,
  scenario    VARCHAR(24)  NOT NULL UNIQUE,   -- QA / MATERIAL / REG_SUMMARY / POLICY_MAP
  provider    VARCHAR(16)  NOT NULL DEFAULT 'LOCAL',
  base_url    TEXT,
  model       VARCHAR(128),
  max_tokens  INT          NOT NULL DEFAULT 1024,
  api_key_enc TEXT,
  key_hint    VARCHAR(16),
  enabled     BOOLEAN      NOT NULL DEFAULT FALSE,   -- 默认停用=回退全局
  updated_by  VARCHAR(64),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai_model_route TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE ai_model_route_id_seq TO grc_app;
