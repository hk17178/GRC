-- =============================================================
-- V70 · B12 低代码 Phase2（D1-8 H-05 自定义列表视图/筛选，隔离红线核心）
-- =============================================================
-- custom_view_def.definition 为声明式 JSON（可见列/筛选/排序），绝不存裸 SQL。
-- 运行期由查询编排器编译为参数化查询，强制：字段白名单（标准列 + custom_field_def 键）、
-- 注入 org_id = ANY(visibleOrgs)、运行在已 SET app.visible_orgs 的 RLS 会话（兜底）。
-- 携 org_id + RLS，视图定义本身也按组织隔离。
-- =============================================================

CREATE TABLE custom_view_def (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                       -- 隔离锚点
  object_type  VARCHAR(32)  NOT NULL,                       -- 宿主对象类型（本期 'ASSET'）
  name         VARCHAR(128) NOT NULL,                       -- 视图名
  definition   TEXT         NOT NULL,                       -- 声明式 JSON：{columns:[],filters:[],sort:{}}
  status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',      -- ACTIVE/RETIRED
  created_by   VARCHAR(64),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_custom_view_obj ON custom_view_def(org_id, object_type, status);

ALTER TABLE custom_view_def ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_custom_view_iso ON custom_view_def
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE ON custom_view_def TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE custom_view_def_id_seq TO grc_app;
