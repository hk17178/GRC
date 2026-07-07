-- =============================================================
-- V72 · B12 低代码 Phase4（D1-8 §七 自定义 KPI）
-- =============================================================
-- kpi_def.formula 为声明式 JSON DSL：{objectType, terms:{a:{agg,field,filters}}, expr, unit, decimals}。
-- 每个 term 为一个"带筛选的标量聚合"（COUNT/SUM/AVG/MIN/MAX），expr 为受限算术表达式（+-*/()）。
-- 求值引擎在已注入 visible_orgs 的 RLS 会话执行标量聚合，数据集经统一访问层。
-- 口径一致性红线：涉及风险等级/风险域时必须复用平台 levelMatrix 与 risk_domain 字典（经统一列白名单），
-- 不得自定义阈值。携 org_id + RLS，KPI 定义本身也按组织隔离。
-- =============================================================

CREATE TABLE kpi_def (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                       -- 隔离锚点
  object_type  VARCHAR(32)  NOT NULL,                       -- 数据集宿主对象类型（本期 'ASSET'）
  name         VARCHAR(128) NOT NULL,                       -- KPI 名
  formula      TEXT         NOT NULL,                       -- 声明式 JSON DSL（terms + expr + unit）
  unit         VARCHAR(16),                                 -- 展示单位（如 %、个）
  status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE/RETIRED
  created_by   VARCHAR(64),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_kpi_def_obj ON kpi_def(org_id, object_type, status);

ALTER TABLE kpi_def ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_kpi_def_iso ON kpi_def
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE ON kpi_def TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE kpi_def_id_seq TO grc_app;
