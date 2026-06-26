-- =============================================================
-- V31 · 风险评估表单引擎 P2：管理层接受签批（CR-002 红线对齐）
-- =============================================================
-- 残余风险为高/极高的评估，须有管理层接受签批方可"完成"（见 AssessmentService.complete 门控）。
-- 签批信息直接挂在 assessment 行（一对一、低频），随 org 行受 RLS 裁剪，无需另表。
-- =============================================================

ALTER TABLE assessment ADD COLUMN IF NOT EXISTS mgmt_signer    VARCHAR(64);   -- 签批人
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS mgmt_signed_at TIMESTAMPTZ;   -- 签批时间
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS mgmt_opinion   TEXT;          -- 管理层意见
ALTER TABLE assessment ADD COLUMN IF NOT EXISTS mgmt_accepted  BOOLEAN NOT NULL DEFAULT FALSE; -- 是否接受残余风险
