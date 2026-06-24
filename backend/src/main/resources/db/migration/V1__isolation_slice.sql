-- =====================================================================
-- V1 · 组织隔离 + RLS 兜底 垂直切片（Flyway 以 owner 角色执行）
-- 设计依据：D1-3 §5.1（统一数据访问层注入 visibleOrgs + 全量 RLS 兜底）
-- 关键：应用以【非 owner 角色 grc_app】连接，故 ENABLE（无需 FORCE）即对其生效；
--       owner 不受 RLS，可正常执行迁移/灌种子。grc_app 需 NOBYPASSRLS（见 deploy/db/init）。
-- =====================================================================

CREATE TABLE org (
  id         BIGINT PRIMARY KEY,
  parent_id  BIGINT REFERENCES org(id),
  org_type   VARCHAR(16) NOT NULL,           -- GROUP / SUBSIDIARY / DEPT
  code       VARCHAR(64) UNIQUE NOT NULL,
  name       VARCHAR(128) NOT NULL,
  path       VARCHAR(512) NOT NULL           -- 物化路径，便于子树展开
);

CREATE TABLE app_user (
  id        BIGINT PRIMARY KEY,
  username  VARCHAR(64) NOT NULL,
  org_id    BIGINT REFERENCES org(id)
);

-- 业务表统一携带 org_id 隔离锚点（代表 D1-2 全部业务实体的统一约定）
CREATE TABLE assessment (
  id         BIGINT PRIMARY KEY,
  org_id     BIGINT NOT NULL REFERENCES org(id),
  title      VARCHAR(256) NOT NULL,
  risk_level VARCHAR(16)                      -- VERY_LOW/LOW/MID/HIGH/VERY_HIGH（平台统一五级）
);
CREATE INDEX idx_assessment_org ON assessment(org_id);

-- ---------- RLS 兜底（最高优先红线） ----------
ALTER TABLE assessment ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_org_iso ON assessment
  USING (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- 运行期账号授权（grc_app 由 deploy/db/init/00_roles.sql 预先创建）
GRANT USAGE ON SCHEMA public TO grc_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO grc_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO grc_app;

-- ---------- 种子：集团 + 两家子公司 + 用户 ----------
INSERT INTO org VALUES
 (1,  NULL, 'GROUP',      'GRP', '集团总部',   '/1'),
 (12, 1,    'SUBSIDIARY', 'PAY', '支付子公司', '/1/12'),
 (13, 1,    'SUBSIDIARY', 'CF',  '消费金融',   '/1/13');

INSERT INTO app_user VALUES
 (1, 'group_admin', 1),
 (2, 'pay_user',    12),
 (3, 'cf_user',     13);

INSERT INTO assessment VALUES
 (101, 12, '支付-核心网关等保自评', 'HIGH'),
 (102, 12, '支付-结算访问控制',     'MID'),
 (201, 13, '消金-数据出境评估',     'VERY_HIGH'),
 (202, 13, '消金-反洗钱模型',       'LOW');
