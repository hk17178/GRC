-- =============================================================
-- V33 · 大模型接入 web 配置（运营在界面配 provider + API Key，替代仅 env 注入）
-- =============================================================
-- 平台级单行配置（集团统一）：provider/base_url/model/max_tokens + 加密后的 api_key。
-- api_key 以 AES 加密存储（密钥来自 GRC_CONFIG_SECRET），接口不回显明文、只给末位掩码。
-- 全局配置无 org_id、不挂 RLS；写操作由功能权限 "ai" 门控。
-- =============================================================

CREATE TABLE ai_provider_config (
  id          BIGINT       PRIMARY KEY,
  provider    VARCHAR(16)  NOT NULL DEFAULT 'LOCAL',   -- LOCAL / CLAUDE / OPENAI
  base_url    TEXT,
  model       VARCHAR(128),
  max_tokens  INT          NOT NULL DEFAULT 1024,
  api_key_enc TEXT,                                    -- AES 加密的密钥（非明文）
  key_hint    VARCHAR(16),                             -- 掩码提示（末 4 位），供界面显示"已配置"
  enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by  VARCHAR(64),
  CONSTRAINT ck_ai_cfg_singleton CHECK (id = 1)
);

-- 初始单行：本地离线（不接外部大模型）
INSERT INTO ai_provider_config (id, provider) VALUES (1, 'LOCAL');

GRANT SELECT, INSERT, UPDATE ON ai_provider_config TO grc_app;
