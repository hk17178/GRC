-- =====================================================================
-- V5 · M2 风险评估（评估生命周期 + 风险发现 + 风险接受，落地 CR-002 残余风险关闭门控红线）
-- 设计依据：D1-2 数据模型（risk_finding/risk_acceptance、五级风险、关闭门控）、
--           D1-6 表单引擎专项设计、D1-3 §5.1（RLS 兜底）、D2-5（编码规范）。
-- 要点：
--   1) 扩展 V1 既有 assessment 最小表：增 status 状态机 / assessor / period，并补可写所需的 WITH CHECK；
--      原 id 为手工赋值 BIGINT（含种子 101/102/201/202），新增独立序列承接 Service 新建，起始 1000 避开种子；
--   2) 新表 risk_finding（风险发现）：inherent/residual 五级，status 状态机 OPEN→TREATING→DONE→VERIFIED，
--      risk_acceptance_id 回填字段（残余高/极高关闭门控的放行凭据）；
--   3) 新表 risk_acceptance（风险接受）：高残余风险关闭的审批凭据；
--   4) 三处 ENABLE RLS + USING/WITH CHECK（与业务表同口径，按 visible_orgs 裁剪含写入校验）；
--   5) 仅授予 grc_app 必要权限；BIGSERIAL/序列需额外 GRANT USAGE,SELECT。
-- 注：本迁移由 Flyway 以 owner 角色执行，PostgreSQL 原生 `::` 强转在此合法；
--    但 Service 层 EntityManager 原生查询禁止用 `::`，统一用 CAST（见 M1 范式注记）。
-- =====================================================================

-- ---------- 扩展既有 assessment（评估生命周期） ----------
-- 评估状态机：DRAFT → IN_PROGRESS → PENDING_REVIEW → COMPLETED
ALTER TABLE assessment ADD COLUMN status     VARCHAR(24) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE assessment ADD COLUMN assessor   VARCHAR(64);
ALTER TABLE assessment ADD COLUMN period     VARCHAR(16);
-- 实体含 @PrePersist/@PreUpdate 维护审计时间戳，需对应列（V1 最小表未含）。
ALTER TABLE assessment ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE assessment ADD COLUMN updated_at TIMESTAMPTZ;

-- 新建评估的主键来源：独立序列，起始 1000（避开 V1 手工种子 101/102/201/202）。
CREATE SEQUENCE assessment_id_seq START WITH 1000 OWNED BY assessment.id;
ALTER TABLE assessment ALTER COLUMN id SET DEFAULT nextval('assessment_id_seq');

-- V1 的 assessment 仅有 USING（只读裁剪）；M2 起 Service 需新建/流转评估，补 WITH CHECK 以放行写入。
DROP POLICY IF EXISTS p_org_iso ON assessment;
CREATE POLICY p_org_iso ON assessment
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT USAGE, SELECT ON SEQUENCE assessment_id_seq TO grc_app;

-- ---------- 风险发现主表 ----------
-- 五级风险取值：VERY_LOW / LOW / MID / HIGH / VERY_HIGH（平台统一五级）
-- 状态机：OPEN → TREATING → DONE → VERIFIED
CREATE TABLE risk_finding (
  id                  BIGSERIAL PRIMARY KEY,
  org_id              BIGINT       NOT NULL REFERENCES org(id),         -- 隔离锚点
  assessment_id       BIGINT       NOT NULL REFERENCES assessment(id),  -- 所属评估
  title               VARCHAR(256),                                     -- 风险描述
  inherent_level      VARCHAR(12),                                      -- 固有风险等级（五级）
  treatment_plan      TEXT,                                             -- 处置方案
  residual_level      VARCHAR(12),                                      -- 残余风险等级（五级，关闭门控判定依据）
  risk_acceptance_id  BIGINT,                                           -- 回填：有效风险接受凭据（高残余关闭放行）
  status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN',             -- 状态机当前态
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_risk_finding_org ON risk_finding(org_id);
CREATE INDEX idx_risk_finding_assessment ON risk_finding(assessment_id);

-- ---------- 风险接受表（高残余风险关闭的审批凭据） ----------
CREATE TABLE risk_acceptance (
  id           BIGSERIAL PRIMARY KEY,
  org_id       BIGINT       NOT NULL REFERENCES org(id),               -- 隔离锚点
  finding_id   BIGINT       NOT NULL REFERENCES risk_finding(id),      -- 所接受的风险发现
  approver     VARCHAR(64)  NOT NULL,                                  -- 接受审批人
  reason       TEXT,                                                   -- 接受理由
  accepted_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_risk_acceptance_finding ON risk_acceptance(finding_id);

-- 回填外键（finding → 其有效 acceptance），在 risk_acceptance 建表后补加。
ALTER TABLE risk_finding
  ADD CONSTRAINT fk_risk_finding_acceptance
  FOREIGN KEY (risk_acceptance_id) REFERENCES risk_acceptance(id);

-- ---------- 组织隔离：与业务表同口径，按 visible_orgs 裁剪（含写入校验） ----------
ALTER TABLE risk_finding ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_risk_finding_iso ON risk_finding
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

ALTER TABLE risk_acceptance ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_risk_acceptance_iso ON risk_acceptance
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权（grc_app 由 deploy/db/init / testcontainers-init 预先创建） ----------
GRANT SELECT, INSERT, UPDATE ON risk_finding TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE risk_finding_id_seq TO grc_app;
GRANT SELECT, INSERT, UPDATE ON risk_acceptance TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE risk_acceptance_id_seq TO grc_app;
