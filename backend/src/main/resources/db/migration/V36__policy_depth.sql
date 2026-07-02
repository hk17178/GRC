-- =============================================================
-- V36 · M1 制度体系深度（对照评估 P2：元数据 / 版本历史 / 引用关系）
-- =============================================================
-- 1) policy 加元数据列：体系分类 / 生效日期 / 复审周期 / 责任部门 / 责任人（需求 3.2.1）。
-- 2) policy_version：版本历史快照——修订时把旧版(标题+正文)存档，支持版本时间线与回看。
-- 3) policy_ref：制度间引用关系（如实施细则引用管理办法）。
-- 新表携 org_id + RLS，与业务表同口径。
-- =============================================================

ALTER TABLE policy ADD COLUMN IF NOT EXISTS framework           VARCHAR(16);  -- 体系分类(ISO27001/MLPS/PIPL/PBOC/PCI_DSS/GENERAL)
ALTER TABLE policy ADD COLUMN IF NOT EXISTS effective_date      DATE;         -- 生效日期
ALTER TABLE policy ADD COLUMN IF NOT EXISTS review_cycle_months INT;          -- 复审周期(月)
ALTER TABLE policy ADD COLUMN IF NOT EXISTS owner_dept          VARCHAR(64);  -- 责任部门
ALTER TABLE policy ADD COLUMN IF NOT EXISTS owner               VARCHAR(64);  -- 责任人

CREATE TABLE policy_version (
  id         BIGSERIAL    PRIMARY KEY,
  org_id     BIGINT       NOT NULL,
  policy_id  BIGINT       NOT NULL,
  version_no INT          NOT NULL,                -- 被归档的版本号
  title      VARCHAR(256),
  content    TEXT,
  note       TEXT,                                 -- 修订说明（新版本为什么改）
  changed_by VARCHAR(64),
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
ALTER TABLE policy_version ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_policy_version_iso ON policy_version
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT ON policy_version TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE policy_version_id_seq TO grc_app;

CREATE TABLE policy_ref (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL,
  policy_id     BIGINT       NOT NULL,             -- 引用方
  ref_policy_id BIGINT       NOT NULL,             -- 被引用制度
  note          VARCHAR(256),                      -- 引用说明（如"第3章引用其密码策略"）
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_policy_ref UNIQUE (policy_id, ref_policy_id)
);
ALTER TABLE policy_ref ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_policy_ref_iso ON policy_ref
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, DELETE ON policy_ref TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE policy_ref_id_seq TO grc_app;
