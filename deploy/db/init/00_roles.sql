-- 容器首次初始化时由 postgres 超级用户执行（/docker-entrypoint-initdb.d）。
-- 创建运行期应用账号：可登录、非超级用户、【不绕过 RLS】。
-- 这是隔离红线的前提——应用必须以受 RLS 约束的角色连接。
CREATE ROLE grc_app LOGIN PASSWORD 'grc_app_pw' NOBYPASSRLS;
