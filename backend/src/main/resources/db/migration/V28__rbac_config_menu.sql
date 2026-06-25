-- =============================================================
-- V28 · 权限配置菜单资源（增强③ R5）
-- =============================================================
-- 新增"权限配置"菜单资源，供 RBAC 配置界面做菜单门控（仅超管/被授权角色可见可改）。
-- =============================================================
INSERT INTO resource (code, name, type, sort) VALUES
  ('rbacconfig','权限配置','MENU',20);

-- 运行期账号经配置界面建角色/改矩阵：授予 role 表写权 + 序列
GRANT SELECT, INSERT, UPDATE ON role TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE role_id_seq TO grc_app;
