-- =====================================================================
-- V6 · M3 审计管理（审计计划生命周期 + 审计发现，落地"外部审计对外回函三段漏斗"红线）
-- 设计依据：需求文档 M3 审计管理（内审/外审/监管检查/认证审计 audit_type；外审三段漏斗）、
--           D1-3 §5.1（RLS 兜底）、D2-5（编码规范）。
-- 要点：
--   1) 扩展 V3 既有 audit_plan（V3 由调度内核建表，含 plan_start_date/reminder_days/external_status，
--      ExpiryScanService 扫描它产 EXT_AUDIT_PLAN_APPROACHING）——本迁移【只新增列、不动既有列与调度】：
--        · audit_type：内审/外审/监管检查/认证审计（默认 EXTERNAL，兼容既有"外审计划"语义与调度扫描）；
--        · status：审计计划生命周期状态机 PLANNED→IN_PROGRESS→REPORTING→CLOSED（另 PLANNED/IN_PROGRESS→CANCELLED）；
--        · created_at/updated_at：实体 @PrePersist/@PreUpdate 维护审计时间戳。
--      新增列均给默认值/可空 → 不破坏 ExpiryScanKernelTest 仅 INSERT(org_id,title,plan_start_date,reminder_days)，
--      也不破坏 V3 调度（external_status 列保留，扫描条件 external_status='PLANNED' 不受影响）。
--   2) audit_plan 的 RLS（V3 已建 ENABLE + USING + WITH CHECK，写入校验已具备）——核对无需补 WITH CHECK；
--      为 audit_plan 增独立序列（V3 用 BIGSERIAL，已有 audit_plan_id_seq，Service @GeneratedValue(IDENTITY) 沿用）。
--   3) 新表 audit_finding（审计发现）：severity 平台五级口径(VERY_LOW/LOW/MID/HIGH/VERY_HIGH，与 RiskLevel 同口径)，
--      status 状态机 OPEN→ANALYZING→REMEDIATED→CLOSED；external_response_status 外审回函三段漏斗（仅外审用，可空）；
--      ENABLE RLS + USING/WITH CHECK（按 visible_orgs 裁剪含写入校验）；
--      GRANT + 序列 GRANT（BIGSERIAL 需额外 GRANT USAGE,SELECT）。
-- 注：本迁移由 Flyway 以 owner 角色执行，PostgreSQL 原生 `::` 强转在此合法；
--    但 Service 层 EntityManager 原生查询禁止用 `::`，统一用 CAST（见 M1/M2 范式注记）。
-- =====================================================================

-- ---------- 扩展既有 audit_plan（V3 建；只新增列，不动既有列/索引/RLS/调度用法） ----------
-- audit_type 取值：INTERNAL(内审) / EXTERNAL(外审) / REGULATORY(监管检查) / CERTIFICATION(认证审计)
-- 默认 EXTERNAL：兼容 V3 既有"外审计划"语义；ExpiryScanService 不依赖此列，扫描行为不变。
ALTER TABLE audit_plan ADD COLUMN audit_type VARCHAR(16) NOT NULL DEFAULT 'EXTERNAL';
-- 审计计划生命周期状态机：PLANNED → IN_PROGRESS → REPORTING → CLOSED（另 PLANNED/IN_PROGRESS → CANCELLED）
-- 独立于 V3 既有 external_status（调度专用，仍由内核读取），互不影响。
ALTER TABLE audit_plan ADD COLUMN status     VARCHAR(24) NOT NULL DEFAULT 'PLANNED';
-- 实体含 @PrePersist/@PreUpdate 维护审计时间戳（V3 最小表未含）。可空，旧行与测试 INSERT 不受影响。
ALTER TABLE audit_plan ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE audit_plan ADD COLUMN updated_at TIMESTAMPTZ;

-- 核对：V3 已对 audit_plan ENABLE RLS 并建 p_audit_plan_iso（USING + WITH CHECK 均具备），
-- 写入校验已就绪，M3 Service 写入可被放行，无需补 WITH CHECK。
-- 主键序列：V3 BIGSERIAL 已建 audit_plan_id_seq 且已 GRANT；Service 用 @GeneratedValue(IDENTITY) 直接沿用。

-- ---------- 审计发现主表（M3 核心；外审回函三段漏斗承载于 external_response_status） ----------
-- severity 严重度：VERY_LOW / LOW / MID / HIGH / VERY_HIGH（平台五级口径，与 RiskLevel 同口径）
-- status 状态机：OPEN → ANALYZING → REMEDIATED → CLOSED
-- external_response_status 外审回函三段漏斗（红线，仅 audit_type=EXTERNAL 的发现可用，可空）：
--   SUBMITTED(已提交外部机构) → ACCEPTED(外方受理) → CLOSED(外方确认关闭)
--   单向推进，不允许跳级、不允许逆向；唯 CLOSED 算外审闭环（由 AuditFindingService 强校验）。
CREATE TABLE audit_finding (
  id                        BIGSERIAL PRIMARY KEY,
  org_id                    BIGINT       NOT NULL REFERENCES org(id),          -- 隔离锚点
  audit_plan_id             BIGINT       NOT NULL REFERENCES audit_plan(id),   -- 所属审计计划
  title                     VARCHAR(256),                                      -- 发现描述
  severity                  VARCHAR(12),                                       -- 严重度（VERY_LOW/LOW/MID/HIGH/VERY_HIGH）
  status                    VARCHAR(16)  NOT NULL DEFAULT 'OPEN',              -- 发现处置状态机当前态
  external_response_status  VARCHAR(24),                                       -- 外审回函三段漏斗（仅外审用，可空）
  created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_finding_org ON audit_finding(org_id);
CREATE INDEX idx_audit_finding_plan ON audit_finding(audit_plan_id);

-- ---------- 组织隔离：与业务表同口径，按 visible_orgs 裁剪（含写入校验） ----------
ALTER TABLE audit_finding ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_audit_finding_iso ON audit_finding
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权（grc_app 由 deploy/db/init / testcontainers-init 预先创建） ----------
GRANT SELECT, INSERT, UPDATE ON audit_finding TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE audit_finding_id_seq TO grc_app;
