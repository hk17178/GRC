-- =============================================================
-- V46 · 风险评估 R1：背景建立（ISO/IEC 27005 / GB/T 20984 ①阶段）
-- 评估元数据：范围/目的/依据标准/方式方法/评估准则/评估组/起止时间。
-- 报告导出时以保留占位符（"$"+"{评估范围}" 等，此处不能原样书写——Flyway 会把注释里的
-- 占位符语法当自身 placeholder 解析）回填进 docx 模板，见 AssessmentFormService.contextPlaceholders。
-- =============================================================
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS scope      TEXT;           -- 评估范围与边界（系统/业务/部门/场所）
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS objective  TEXT;           -- 评估目的与背景
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS basis      VARCHAR(256);   -- 依据标准（逗号多值：ISO27001/GBT20984/MLPS/PCI_DSS/PIPL/…）
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS methods    VARCHAR(128);   -- 方式方法（逗号多值：INTERVIEW/DOC_REVIEW/TOOL_SCAN/PENTEST/CONFIG_CHECK）
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS criteria   TEXT;           -- 评估准则（可能性/影响分级与接受准则说明）
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS team       VARCHAR(256);   -- 评估组成员
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS start_date DATE;           -- 评估开始日
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS end_date   DATE;           -- 评估结束日
