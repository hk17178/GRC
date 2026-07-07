-- =============================================================
-- V71 · B12 低代码 Phase3（D1-8 §六 自定义报表）
-- =============================================================
-- custom_report_def.definition 为声明式 JSON（维度 groupBy + 度量 measures + 筛选），绝不存裸 SQL。
-- 运行期由报表编排器编译为参数化聚合查询，强制：维度/度量字段白名单（标准列 + custom_field_def 键）、
-- 聚合函数枚举（COUNT/SUM/AVG/MIN/MAX）、运行在已注入 visible_orgs 的 RLS 会话（数据集经统一访问层）。
-- 导出动作入 operation_log（哈希链留痕）。携 org_id + RLS，报表定义本身也按组织隔离。
-- =============================================================

CREATE TABLE custom_report_def (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                       -- 隔离锚点
  object_type  VARCHAR(32)  NOT NULL,                       -- 数据集宿主对象类型（本期 'ASSET'）
  name         VARCHAR(128) NOT NULL,                       -- 报表名
  definition   TEXT         NOT NULL,                       -- 声明式 JSON：{groupBy:[],measures:[{field,agg}],filters:[]}
  status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE/RETIRED
  created_by   VARCHAR(64),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_custom_report_obj ON custom_report_def(org_id, object_type, status);

ALTER TABLE custom_report_def ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_custom_report_iso ON custom_report_def
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE ON custom_report_def TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE custom_report_def_id_seq TO grc_app;
