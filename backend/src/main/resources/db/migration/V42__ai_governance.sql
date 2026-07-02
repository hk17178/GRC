-- =============================================================
-- V42 · AI 治理：模型白名单 + 提示词模板（平台级，随 V33 同型）
-- =============================================================
-- kind='MODEL_WHITELIST'：name=允许接入的模型 id（如 claude-sonnet-4-5），detail=备注；
--   存在启用的白名单条目时，模型接入配置（非 LOCAL）的 model 必须命中白名单，否则拒绝保存。
-- kind='PROMPT_TEMPLATE'：name=模板名，detail=系统提示词正文（材料生成/变更摘要等场景引用）。
-- 平台级无 org_id、不挂 RLS；写操作由功能权限 "ai" 门控（与 ai_provider_config 一致）。
-- =============================================================

CREATE TABLE ai_governance (
  id          BIGSERIAL    PRIMARY KEY,
  kind        VARCHAR(24)  NOT NULL,                    -- MODEL_WHITELIST / PROMPT_TEMPLATE
  name        VARCHAR(128) NOT NULL,
  detail      TEXT,
  enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by  VARCHAR(64)
);
CREATE INDEX idx_ai_governance_kind ON ai_governance(kind);

-- 种子：常用模型白名单 + 两个提示词模板（可在界面增删改）
INSERT INTO ai_governance (kind, name, detail) VALUES
  ('MODEL_WHITELIST', 'claude-sonnet-4-5', 'Anthropic Claude Sonnet（推荐）'),
  ('MODEL_WHITELIST', 'claude-haiku-4-5', 'Anthropic Claude Haiku（轻量）'),
  ('PROMPT_TEMPLATE', '管理层简报', '请基于以下真实合规统计，为管理层撰写一份简明的合规态势简报（中文，分要点：总体态势/主要风险/整改与审计/建议，400 字内）。'),
  ('PROMPT_TEMPLATE', '监管报送稿', '请基于以下真实合规统计，起草一份对监管机构的定期报送材料初稿（中文，正式公文口吻，含 总体情况/风险与整改/合规义务履行 三节，500 字内）。');

GRANT SELECT, INSERT, UPDATE, DELETE ON ai_governance TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE ai_governance_id_seq TO grc_app;
