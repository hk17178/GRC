-- =====================================================================
-- V4 · M1 制度体系（一个完整业务纵切，作为后续模块开发范式）
-- 设计依据：D1-2（业务实体统一携带 org_id）、D1-3 §5.1（RLS 兜底）、D2-5（编码规范）。
-- 要点：
--   1) policy 制度主表 + policy_signoff 签署确认表，均携带 org_id 隔离锚点；
--   2) 状态机：DRAFT → PENDING_APPROVAL → PUBLISHED → ARCHIVED，PENDING_APPROVAL 可驳回回 DRAFT；
--   3) 两表 ENABLE RLS + USING/WITH CHECK（与业务表同口径，按 visible_orgs 裁剪，含写入校验）；
--   4) 仅授予 grc_app 必要权限；BIGSERIAL 序列需额外 GRANT USAGE,SELECT。
-- 注：本迁移由 Flyway 以 owner 角色执行，PostgreSQL 原生 `::` 强转在此完全合法；
--    但 Service 层 EntityManager 原生查询禁止用 `::`（Hibernate 把 : 当命名参数会报错），统一用 CAST。
-- =====================================================================

-- ---------- 制度主表 ----------
CREATE TABLE policy (
  id          BIGSERIAL PRIMARY KEY,
  org_id      BIGINT       NOT NULL REFERENCES org(id),    -- 隔离锚点
  code        VARCHAR(64),                                 -- 制度编号（org 内唯一）
  title       VARCHAR(256),                                -- 制度标题
  content     TEXT,                                        -- 制度正文
  status      VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',       -- 状态机当前态
  version     INT          NOT NULL DEFAULT 1,             -- 版本号
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (org_id, code)                                    -- 同一组织内编号唯一
);
CREATE INDEX idx_policy_org ON policy(org_id);

-- ---------- 制度签署确认表（PUBLISHED 后由相关人员逐一签署确认已阅知/承诺执行） ----------
CREATE TABLE policy_signoff (
  id         BIGSERIAL PRIMARY KEY,
  policy_id  BIGINT       NOT NULL REFERENCES policy(id),  -- 所属制度
  org_id     BIGINT       NOT NULL REFERENCES org(id),     -- 隔离锚点（冗余自 policy，便于 RLS 与索引）
  signer     VARCHAR(64)  NOT NULL,                        -- 签署人
  signed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (policy_id, signer)                               -- 同一制度同一签署人只签一次（幂等保证）
);
CREATE INDEX idx_policy_signoff_policy ON policy_signoff(policy_id);

-- ---------- 组织隔离：与业务表同口径，按 visible_orgs 裁剪（含写入校验） ----------
ALTER TABLE policy ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_policy_iso ON policy
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE policy_signoff ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_policy_signoff_iso ON policy_signoff
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权（grc_app 由 deploy/db/init / testcontainers-init 预先创建） ----------
GRANT SELECT, INSERT, UPDATE ON policy TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE policy_id_seq TO grc_app;
GRANT SELECT, INSERT ON policy_signoff TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE policy_signoff_id_seq TO grc_app;
