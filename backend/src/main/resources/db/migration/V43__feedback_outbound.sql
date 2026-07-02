-- =============================================================
-- V43 · 反馈出站审批（对外回复须经审批后方可出站）
-- outbound_reply  对外回复稿正文；
-- outbound_status 出站审批状态：NULL 未发起 / PENDING_APPROVAL 待审 / APPROVED 已批准 / REJECTED 已驳回（可改稿重发）。
-- 审批走 Flowable（bizType=FEEDBACK_OUTBOUND，审批组 FEEDBACK_OUTBOUND_APPROVER）。
-- =============================================================
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS outbound_reply  TEXT;
ALTER TABLE feedback ADD COLUMN IF NOT EXISTS outbound_status VARCHAR(20);
