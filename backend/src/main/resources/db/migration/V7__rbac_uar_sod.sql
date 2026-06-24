-- =====================================================================
-- V7 · M8 权限与审批（RBAC + 权限四元组 + SoD 职责分离红线 + UAR 权限审阅）
-- 设计依据：需求文档 M8 权限审批（RBAC、权限四元组、UAR、SoD）、D1-3 §4.7（UAR/SoD）、D2-5（编码规范）。
-- 要点：
--   1) 全局字典表（不含 org_id，不启 RLS）：role / permission / role_permission / sod_rule；
--      互斥角色对（sod_rule）在全平台口径一致，故为全局字典；grc_app 仅授 SELECT（字典只读，由 owner 灌种子）。
--   2) org-scoped 业务表（含 org_id，ENABLE RLS + USING/WITH CHECK 按 visible_orgs 裁剪含写入校验）：
--        · user_role_org（权限四元组核心：org × user × role × active，授权/回收的载体）；
--        · sod_exception（经审批的 SoD 豁免，放行原本被互斥红线拦截的授权）；
--        · access_review / access_review_item（UAR 权限审阅主表与逐项决定）。
--   3) user_role_org 引用 app_user(id)（V1 既有，本迁移不改其结构）；不破坏 IsolationRlsTest/VisibleOrgsService。
--   4) BIGSERIAL/序列需额外 GRANT USAGE,SELECT；新表被其它新表 FK 引用，既有测试 TRUNCATE 需 CASCADE。
-- 注：本迁移由 Flyway 以 owner 角色执行，PostgreSQL 原生 `::` 强转在此合法；
--    但 Service 层 EntityManager 原生查询禁止用 `::`，统一用 CAST（见 M1/M2/M3 范式注记）。
-- =====================================================================

-- ---------- 全局字典：角色 / 权限 / 角色-权限 / 互斥规则（不含 org_id，不启 RLS） ----------

-- 角色字典（全局）：code 唯一业务码，name 显示名。
CREATE TABLE role (
  id    BIGSERIAL PRIMARY KEY,
  code  VARCHAR(64)  UNIQUE NOT NULL,
  name  VARCHAR(128) NOT NULL
);

-- 权限字典（全局）：权限点 code 唯一。
CREATE TABLE permission (
  id    BIGSERIAL PRIMARY KEY,
  code  VARCHAR(64)  UNIQUE NOT NULL,
  name  VARCHAR(128) NOT NULL
);

-- 角色-权限关联（全局，复合主键）。
CREATE TABLE role_permission (
  role_id       BIGINT NOT NULL REFERENCES role(id),
  permission_id BIGINT NOT NULL REFERENCES permission(id),
  PRIMARY KEY (role_id, permission_id)
);

-- SoD 互斥规则（全局字典）：role_a 与 role_b 互斥，同一 user 在同一 org 不得同时持有（除非经审批豁免）。
CREATE TABLE sod_rule (
  id          BIGSERIAL PRIMARY KEY,
  role_a_id   BIGINT NOT NULL REFERENCES role(id),
  role_b_id   BIGINT NOT NULL REFERENCES role(id),
  description VARCHAR(256)
);
CREATE INDEX idx_sod_rule_a ON sod_rule(role_a_id);
CREATE INDEX idx_sod_rule_b ON sod_rule(role_b_id);

-- ---------- org-scoped：权限四元组核心 user_role_org ----------
-- 权限四元组 = (org_id, user_id, role_id, active)；同 org 下同一 user 对同一 role 至多一行（UNIQUE）。
-- 授权写 active=true；回收置 active=false（不物理删除，保留留痕可追溯）。
CREATE TABLE user_role_org (
  id          BIGSERIAL PRIMARY KEY,
  org_id      BIGINT       NOT NULL REFERENCES org(id),        -- 隔离锚点
  user_id     BIGINT       NOT NULL REFERENCES app_user(id),   -- 被授权用户（V1 既有 app_user）
  role_id     BIGINT       NOT NULL REFERENCES role(id),       -- 角色
  granted_by  VARCHAR(64),                                     -- 授权人 actor
  granted_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  active      BOOLEAN      NOT NULL DEFAULT true,              -- 是否有效（回收/被审阅撤销置 false）
  UNIQUE (org_id, user_id, role_id)
);
CREATE INDEX idx_user_role_org_org ON user_role_org(org_id);
CREATE INDEX idx_user_role_org_user ON user_role_org(user_id);

-- ---------- org-scoped：SoD 豁免（经审批后放行互斥授权） ----------
CREATE TABLE sod_exception (
  id           BIGSERIAL PRIMARY KEY,
  org_id       BIGINT       NOT NULL REFERENCES org(id),       -- 隔离锚点
  user_id      BIGINT       NOT NULL REFERENCES app_user(id),  -- 被豁免用户
  sod_rule_id  BIGINT       NOT NULL REFERENCES sod_rule(id),  -- 所豁免的互斥规则
  approver     VARCHAR(64)  NOT NULL,                          -- 审批人
  reason       TEXT,                                           -- 豁免理由
  approved_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_sod_exception_lookup ON sod_exception(org_id, user_id, sod_rule_id);

-- ---------- org-scoped：UAR 权限审阅主表 ----------
-- 状态机：OPEN → IN_REVIEW → COMPLETED（由 AccessReviewService 强校验，非法流转抛 IllegalStateException）。
CREATE TABLE access_review (
  id        BIGSERIAL PRIMARY KEY,
  org_id    BIGINT       NOT NULL REFERENCES org(id),          -- 隔离锚点
  period    VARCHAR(16),                                       -- 审阅周期（如 2026Q2）
  status    VARCHAR(16)  NOT NULL DEFAULT 'OPEN',              -- 状态机当前态
  reviewer  VARCHAR(64),                                       -- 审阅人
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_access_review_org ON access_review(org_id);

-- ---------- org-scoped：UAR 逐项审阅决定 ----------
-- decision：PENDING(待决) / KEEP(保留) / REVOKE(撤销，撤销则把对应 user_role_org.active=false)。
CREATE TABLE access_review_item (
  id                BIGSERIAL PRIMARY KEY,
  org_id            BIGINT       NOT NULL REFERENCES org(id),                    -- 隔离锚点
  access_review_id  BIGINT       NOT NULL REFERENCES access_review(id),          -- 所属审阅
  user_role_org_id  BIGINT       NOT NULL REFERENCES user_role_org(id),          -- 被审阅的授权四元组
  decision          VARCHAR(12)  NOT NULL DEFAULT 'PENDING',                     -- 审阅决定
  reviewed_at       TIMESTAMPTZ
);
CREATE INDEX idx_access_review_item_review ON access_review_item(access_review_id);
CREATE INDEX idx_access_review_item_uro ON access_review_item(user_role_org_id);

-- ---------- 组织隔离：org-scoped 表按 visible_orgs 裁剪（含写入校验），与业务表同口径 ----------
ALTER TABLE user_role_org ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_user_role_org_iso ON user_role_org
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE sod_exception ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_sod_exception_iso ON sod_exception
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE access_review ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_access_review_iso ON access_review
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE access_review_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_access_review_item_iso ON access_review_item
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权（grc_app 由 deploy/db/init / testcontainers-init 预先创建） ----------
-- 全局字典只读（由 owner 灌种子；应用不增删字典）。
GRANT SELECT ON role TO grc_app;
GRANT SELECT ON permission TO grc_app;
GRANT SELECT ON role_permission TO grc_app;
GRANT SELECT ON sod_rule TO grc_app;

-- org-scoped 业务表：应用可读写（DELETE 不授，回收用 active=false 软删，与留痕可追溯一致）。
GRANT SELECT, INSERT, UPDATE ON user_role_org TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE user_role_org_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON sod_exception TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE sod_exception_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON access_review TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE access_review_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON access_review_item TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE access_review_item_id_seq TO grc_app;

-- ---------- 种子：角色 / 互斥规则（全局字典，便于 SoD 红线测试与默认装配） ----------
-- 经典 SoD 互斥：发起人(MAKER) 与 审批人(CHECKER) 不得由同一人在同一 org 兼任。
-- 注：role/sod_rule 主键为 BIGSERIAL（DEFAULT nextval，非 GENERATED 标识列），
-- 故显式给 id 即可（无需 OVERRIDING SYSTEM VALUE，那是标识列专用语法）；插入后用 setval 推进序列避免撞号。
INSERT INTO role (id, code, name) VALUES
  (1, 'MAKER',         '发起人'),
  (2, 'CHECKER',       '审批人'),
  (3, 'RISK_OWNER',    '风险责任人'),
  (4, 'AUDITOR',       '审计员');
SELECT setval('role_id_seq', (SELECT max(id) FROM role));

INSERT INTO sod_rule (id, role_a_id, role_b_id, description) VALUES
  (1, 1, 2, '发起人(MAKER)与审批人(CHECKER)职责分离，同一用户同一组织不得兼任'),
  (2, 3, 4, '风险责任人(RISK_OWNER)与审计员(AUDITOR)职责分离');
SELECT setval('sod_rule_id_seq', (SELECT max(id) FROM sod_rule));
