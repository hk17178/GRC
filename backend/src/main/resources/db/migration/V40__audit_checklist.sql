-- =============================================================
-- V40 内部审计检查表接表单引擎（M3 深度）
-- 审计计划可绑定评估模板（docx 检查表，复用 V30 表单引擎）；
-- 执行检查表 = 以该模板生成一份评估（assessment），沿用渲染/填写/导出全链路。
-- =============================================================
ALTER TABLE audit_plan ADD COLUMN IF NOT EXISTS checklist_template_id   BIGINT;   -- 绑定的检查表模板（assessment_template.id）
ALTER TABLE audit_plan ADD COLUMN IF NOT EXISTS checklist_assessment_id BIGINT;   -- 执行产生的评估（assessment.id，未执行为空）
