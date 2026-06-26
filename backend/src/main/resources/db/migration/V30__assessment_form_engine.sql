-- =============================================================
-- V30 · 风险评估表单引擎 P1（D1-6 落地起步）
-- =============================================================
-- template_form：挂在 assessment_template 下的"表单模板"——上传的 .docx 原件 + 解析出的表单 schema。
--   一个评估模板可有多版本表单，仅一条 ACTIVE。docx 留作 P3 回填导出官方格式报告。
-- assessment_answer：评估的填写结果（启动时绑定 form 版本=快照，answers_json 存 JSONB）。
-- 二表携 org_id + RLS 按 visible_orgs 裁剪，与业务表同口径。
-- =============================================================

CREATE TABLE template_form (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                       -- 隔离锚点
  template_id  BIGINT       NOT NULL,                       -- 所属评估模板(assessment_template)
  version_no   INT          NOT NULL DEFAULT 1,
  name         VARCHAR(128),
  docx         BYTEA,                                       -- 上传的 .docx 原件（P3 回填导出用）
  schema_json  TEXT         NOT NULL,                       -- 解析出的表单结构(章节/字段/明细表)
  status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',       -- DRAFT/ACTIVE/RETIRED
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE template_form ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_template_form_iso ON template_form
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON template_form TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE template_form_id_seq TO grc_app;
-- 同模板仅一条 ACTIVE 表单
CREATE UNIQUE INDEX uk_template_form_active ON template_form (template_id) WHERE status = 'ACTIVE';

CREATE TABLE assessment_answer (
  id              BIGSERIAL    PRIMARY KEY,
  org_id          BIGINT       NOT NULL,
  assessment_id   BIGINT       NOT NULL UNIQUE,             -- 一个评估一份填写
  form_version_id BIGINT       NOT NULL,                    -- 启动时绑定的 template_form（快照）
  answers_json    TEXT         NOT NULL DEFAULT '{}',       -- 填写值 JSON（按字段 key；P1 用 TEXT，无需 jsonb 查询）
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 评估关联其来源模板（可空：未用表单引擎的旧评估仍兼容）。表单渲染据此找模板的 ACTIVE 表单。
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS template_id BIGINT;

ALTER TABLE assessment_answer ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_assessment_answer_iso ON assessment_answer
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON assessment_answer TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE assessment_answer_id_seq TO grc_app;
