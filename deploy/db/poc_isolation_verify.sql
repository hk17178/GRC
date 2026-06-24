-- =====================================================================
-- GRC 平台 · 组织隔离 + RLS 兜底 垂直切片 PoC（可独立运行验证）
-- 验证第一红线：即使应用层过滤被绕过，数据库行级安全(RLS)仍拦截跨子公司越权。
-- 运行：在 PostgreSQL 16 中以超级用户执行本文件建表/种子，再以 grc_app 角色验证。
-- =====================================================================

-- ---------- 1. Schema ----------
DROP TABLE IF EXISTS assessment CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;
DROP TABLE IF EXISTS org CASCADE;

CREATE TABLE org (
  id        BIGINT PRIMARY KEY,
  parent_id BIGINT REFERENCES org(id),
  org_type  VARCHAR(16) NOT NULL,            -- GROUP / SUBSIDIARY / DEPT
  code      VARCHAR(64) UNIQUE NOT NULL,
  name      VARCHAR(128) NOT NULL,
  path      VARCHAR(512) NOT NULL            -- 物化路径，便于子树展开
);

CREATE TABLE app_user (
  id       BIGINT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  org_id   BIGINT REFERENCES org(id)
);

-- 业务表：带 org_id 隔离锚点（代表 D1-2 全部业务实体的统一约定）
CREATE TABLE assessment (
  id         BIGINT PRIMARY KEY,
  org_id     BIGINT NOT NULL REFERENCES org(id),
  title      VARCHAR(256) NOT NULL,
  risk_level VARCHAR(16)                     -- VERY_LOW/LOW/MID/HIGH/VERY_HIGH（五级）
);
CREATE INDEX idx_assessment_org ON assessment(org_id);

-- ---------- 2. RLS 兜底（最高优先红线） ----------
ALTER TABLE assessment ENABLE ROW LEVEL SECURITY;
ALTER TABLE assessment FORCE  ROW LEVEL SECURITY;   -- 连表 owner 也强制走 RLS
-- 策略：仅可见 org_id ∈ 会话变量 app.visible_orgs（由统一数据访问层在每事务注入）
CREATE POLICY p_org_iso ON assessment
  USING (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 3. 应用账号：非超级用户、不绕过 RLS ----------
DROP ROLE IF EXISTS grc_app;
CREATE ROLE grc_app LOGIN PASSWORD 'grc_app_pw' NOBYPASSRLS;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO grc_app;
GRANT USAGE ON SCHEMA public TO grc_app;

-- ---------- 4. 种子数据：集团 + 两家子公司 ----------
INSERT INTO org VALUES
 (1,  NULL, 'GROUP',     'GRP', '集团总部',   '/1'),
 (12, 1,    'SUBSIDIARY','PAY', '支付子公司', '/1/12'),
 (13, 1,    'SUBSIDIARY','CF',  '消费金融',   '/1/13');

INSERT INTO assessment VALUES
 (101, 12, '支付-核心网关等保自评', 'HIGH'),
 (102, 12, '支付-结算访问控制',     'MID'),
 (201, 13, '消金-数据出境评估',     'VERY_HIGH'),
 (202, 13, '消金-反洗钱模型',       'LOW');
