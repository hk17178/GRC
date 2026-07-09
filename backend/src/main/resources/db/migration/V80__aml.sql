-- =============================================================
-- V80 · 反洗钱 AML（GRC 合规管理视角）
-- =============================================================
-- 范围（合规视角，不做交易监测引擎）：
--   1) 名单管理 aml_watchlist：制裁/PEP/内部黑名单条目，供客户与交易对手筛查；
--   2) 可疑交易报告 str_report：STR 登记 → 内部提交 → 报送反洗钱监测中心 → 了结（生命周期，复用报送范式）；
--   3) 合规义务 复用 obligation（category 标 AML），机构风险自评 复用 assessment —— 本迁移不建表，前端引用。
-- 两表均携 org_id + RLS，按组织隔离；筛查/报送均只在本组织可见域内。
-- =============================================================

-- 名单库（制裁名单 SANCTION / 政治敏感人物 PEP / 内部黑名单 INTERNAL）
CREATE TABLE aml_watchlist (
  id         BIGSERIAL    PRIMARY KEY,
  org_id     BIGINT       NOT NULL,                     -- 隔离锚点
  list_type  VARCHAR(16)  NOT NULL,                     -- SANCTION / PEP / INTERNAL
  name       VARCHAR(128) NOT NULL,                     -- 主体名称
  id_number  VARCHAR(64),                               -- 证件号/组织机构代码（可空）
  country    VARCHAR(64),                               -- 国别/地区
  source     VARCHAR(128),                              -- 名单来源（如 OFAC/联合国/公安部/内部）
  reason     TEXT,                                      -- 列入原因
  status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',    -- ACTIVE / RETIRED
  created_by VARCHAR(64),
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_aml_watchlist_org ON aml_watchlist(org_id, status);
-- 名称/证件号筛查（大小写不敏感前缀+子串匹配走应用层 ILIKE，索引兜底扫描）
CREATE INDEX idx_aml_watchlist_idnum ON aml_watchlist(org_id, id_number);

ALTER TABLE aml_watchlist ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_aml_watchlist_iso ON aml_watchlist
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON aml_watchlist TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE aml_watchlist_id_seq TO grc_app;

-- 可疑交易报告（STR）
CREATE TABLE str_report (
  id            BIGSERIAL    PRIMARY KEY,
  org_id        BIGINT       NOT NULL,                  -- 隔离锚点
  subject       VARCHAR(128) NOT NULL,                  -- 可疑主体（客户/交易对手）
  amount        NUMERIC(18,2),                          -- 涉及金额
  risk_level    VARCHAR(16)  NOT NULL DEFAULT 'MID',    -- LOW / MID / HIGH
  reason        TEXT         NOT NULL,                  -- 可疑理由/情形描述
  status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT → SUBMITTED → REPORTED → CLOSED
  reported_to   VARCHAR(128),                           -- 报送机构（人行反洗钱监测分析中心）
  report_no     VARCHAR(64),                            -- 报送回执号
  occurred_date DATE,                                   -- 可疑交易发生日
  reported_date DATE,                                   -- 报送日
  created_by    VARCHAR(64),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_str_report_org ON str_report(org_id, status);

ALTER TABLE str_report ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_str_report_iso ON str_report
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON str_report TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE str_report_id_seq TO grc_app;
