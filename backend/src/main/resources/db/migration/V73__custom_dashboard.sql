-- =============================================================
-- V73 · B12 低代码 Phase5（D1-8 §五 自定义看板，收官）
-- =============================================================
-- dashboard_def.layout 为声明式 JSON：{widgets:[{type:'KPI'|'REPORT', refId, title}]}。
-- 组件不自取数——只能引用已登记的 kpi_def / custom_report_def（数据源一律走标准聚合接口，
-- 受 M8 + visibleOrgs）；渲染时逐组件解析（KPI 求值 / REPORT 执行），各自经统一访问层 + RLS。
-- 携 org_id + RLS，看板定义本身也按组织隔离。
-- =============================================================

CREATE TABLE dashboard_def (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                       -- 隔离锚点
  name         VARCHAR(128) NOT NULL,                       -- 看板名
  layout       TEXT         NOT NULL,                       -- 声明式 JSON：{widgets:[{type,refId,title}]}
  status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE/RETIRED
  created_by   VARCHAR(64),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_dashboard_def_org ON dashboard_def(org_id, status);

ALTER TABLE dashboard_def ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_dashboard_def_iso ON dashboard_def
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE ON dashboard_def TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE dashboard_def_id_seq TO grc_app;
