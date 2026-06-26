-- =============================================================
-- V29 · 资源目录自管理授权（增强③ R6）
-- =============================================================
-- 启动时由 ResourceCatalog 幂等 upsert 到 resource 表，需 grc_app 具备 resource 写权 + 序列。
-- 此后新增菜单/按钮只需改代码目录，无需再写迁移。
-- =============================================================
GRANT INSERT, UPDATE ON resource TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE resource_id_seq TO grc_app;
