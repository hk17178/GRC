-- =============================================================
-- V56 · 修正内置模板命名歧义（UAT 四轮 #4）
-- V45 种子把 ISO/IEC 27001 模板命名为「内审模板」（指 ISO 内部审核），
-- 但它位于【风险评估】模板库，语义应为风险评估——统一更名，消除与 M3 内部审计的混淆。
-- =============================================================
UPDATE assessment_template
   SET name = 'ISO/IEC 27001 风险评估模板',
       description = '信息安全管理体系附录 A 控制域风险评估脚手架（克隆起步）'
 WHERE org_id = 1 AND code = 'TPL-ISO27001' AND owner = 'platform';

-- 附带（UAT 四轮 #8 去厂商推荐化）：白名单种子备注中性化，不在平台数据里带"推荐"倾向
UPDATE ai_governance SET detail = 'Anthropic 协议模型' WHERE kind = 'MODEL_WHITELIST' AND name = 'claude-sonnet-4-5';
UPDATE ai_governance SET detail = 'Anthropic 协议模型（轻量）' WHERE kind = 'MODEL_WHITELIST' AND name = 'claude-haiku-4-5';
