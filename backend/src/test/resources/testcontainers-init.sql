-- Testcontainers 容器就绪后、Flyway 迁移之前执行（以容器默认 owner 角色）。
-- 创建运行期应用账号 grc_app（非 owner、NOBYPASSRLS），使应用数据源受 RLS 约束。
CREATE ROLE grc_app LOGIN PASSWORD 'grc_app_pw' NOBYPASSRLS;
