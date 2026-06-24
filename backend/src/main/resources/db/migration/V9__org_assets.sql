-- =====================================================================
-- V9 · M6 组织与资产（资产台账[含资产合规属性 CR-002] + 个人信息处理活动 ROPA）
-- 设计依据：需求文档 M6 组织与资产、CR-002（资产合规属性：分类分级/PI/跨境/等保备案/CHD）、
--           个人信息处理活动 ROPA、D1-2、D2-5。
-- 要点：
--   1) 组织树（org）沿用 V1 既有结构与物化路径 path——【本迁移不改动 org 表、不给 org 加 RLS】
--      （org 是组织字典，供 VisibleOrgsService 计算可见域；加 RLS 会破坏可见域计算与 IsolationRlsTest）。
--      V1 已 `GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES` 覆盖 org，故 OrgService 可增改 org 建子组织。
--      org.id 为普通 BIGINT（无序列/默认值），新子组织 id 由 OrgService 取 MAX(id)+1（advisory 锁串行化）后显式插入。
--   2) 新增两张业务台账 asset / ropa，隔离锚点 org_id，均 ENABLE RLS + USING/WITH CHECK（按 visible_orgs 裁剪含写入校验）；
--      与 V8 同口径：GRANT SELECT/INSERT/UPDATE + BIGSERIAL 序列 GRANT USAGE,SELECT 给 grc_app。
--   3) 资产合规属性（CR-002）：classification 分类分级、contains_pi 个人信息、cross_border 跨境、
--      mlps_filed 等保备案、contains_chd 持卡人数据，均给库级默认值（布尔 DEFAULT false），便于按属性筛查。
--   4) 状态字段 NOT NULL DEFAULT；时间戳 created_at/updated_at NOT NULL DEFAULT now()（实体 @PrePersist/@PreUpdate 维护）。
-- 注：本迁移由 Flyway 以 owner 角色执行；Service 层原生查询禁 `::`，统一用 CAST（OrgService 已遵循）。
-- =====================================================================

-- ---------- 资产台账（含资产合规属性 CR-002）----------
-- status 状态机：ACTIVE → RETIRED（停用，终态）。
CREATE TABLE asset (
  id              BIGSERIAL PRIMARY KEY,
  org_id          BIGINT       NOT NULL REFERENCES org(id),       -- 隔离锚点
  name            VARCHAR(256) NOT NULL,                          -- 资产名称
  asset_type      VARCHAR(24),                                    -- 资产类型（SYSTEM/APP/DATABASE/DEVICE 等）
  owner           VARCHAR(64),                                    -- 资产责任人
  -- 资产合规属性（CR-002）
  classification  VARCHAR(16)  NOT NULL DEFAULT 'INTERNAL',       -- 分类分级 PUBLIC/INTERNAL/SENSITIVE（公开/内部/敏感）
  contains_pi     BOOLEAN      NOT NULL DEFAULT false,            -- 是否含个人信息
  cross_border    BOOLEAN      NOT NULL DEFAULT false,            -- 是否涉及数据跨境
  mlps_filed      BOOLEAN      NOT NULL DEFAULT false,            -- 是否已等保备案（MLPS）
  contains_chd    BOOLEAN      NOT NULL DEFAULT false,            -- 是否含持卡人数据（CHD）
  criticality     VARCHAR(12),                                    -- 重要程度（LOW/MEDIUM/HIGH/CRITICAL）
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',         -- 资产生命周期状态机当前态
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_asset_org ON asset(org_id);
CREATE INDEX idx_asset_contains_pi ON asset(contains_pi);

ALTER TABLE asset ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_asset_iso ON asset
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON asset TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE asset_id_seq TO grc_app;

-- ---------- 个人信息处理活动（ROPA）----------
-- status 状态机：DRAFT → ACTIVE → RETIRED（终态）。
CREATE TABLE ropa (
  id               BIGSERIAL PRIMARY KEY,
  org_id           BIGINT       NOT NULL REFERENCES org(id),      -- 隔离锚点
  activity_name    VARCHAR(256) NOT NULL,                         -- 处理活动名称
  purpose          VARCHAR(512),                                  -- 处理目的
  data_categories  TEXT,                                          -- 涉及的个人信息类别
  legal_basis      VARCHAR(32),                                   -- 合法性基础（同意/合同/法定义务等）
  cross_border     BOOLEAN      NOT NULL DEFAULT false,           -- 是否涉及跨境传输
  retention        VARCHAR(64),                                   -- 留存期限
  status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',         -- ROPA 生命周期状态机当前态
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ropa_org ON ropa(org_id);

ALTER TABLE ropa ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_ropa_iso ON ropa
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON ropa TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE ropa_id_seq TO grc_app;
