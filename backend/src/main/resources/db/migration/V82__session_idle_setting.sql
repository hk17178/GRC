-- =============================================================
-- V82 · 会话安全：无操作自动登出时长（系统设置基础项，用户反馈）
-- =============================================================
-- 前端据此在空闲达阈值后自动登出（清会话 + 跳登录），降低无人值守终端被盗用风险。
-- 集团组织(org 1)一条，可在「系统设置」修改（editable）。
-- =============================================================

INSERT INTO system_setting (org_id, setting_key, setting_value, value_type, category, description, editable)
VALUES (1, 'security.session.idle-minutes', '30', 'INT', 'security',
        '无操作自动登出的空闲分钟数——达到该时长无鼠标/键盘操作则自动退出登录（会话安全）', true)
ON CONFLICT (org_id, setting_key) DO NOTHING;
