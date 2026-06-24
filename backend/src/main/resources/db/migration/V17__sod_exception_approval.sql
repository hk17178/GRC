-- =============================================================
-- V17 · SoD 豁免审批化（A4：M8 SoD 例外走 Flowable 审批）
-- =============================================================
-- 原 sod_exception 为「登记即生效」；改为「申请→审批」两段式：申请置 PENDING（不放行），
-- 审批通过才置 APPROVED 并作为有效豁免（enforceSod 仅认 APPROVED）。以新增列兼容已有行。
--
-- 列变更：
--   + requester    申请人
--   + status       审批状态 PENDING/APPROVED/REJECTED（历史行视为已通过 → 默认回填 APPROVED）
--   ~ approver     放宽可空（PENDING 申请尚无审批人；通过/驳回时落定）
--   ~ approved_at  放宽可空（审批落定时才置）
-- =============================================================

ALTER TABLE sod_exception ADD COLUMN requester VARCHAR(64);
-- 历史行（旧「登记即生效」语义）等同已通过，先以 APPROVED 回填既有行；
ALTER TABLE sod_exception ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'APPROVED';
-- 回填后把列默认改为 PENDING，使后续直插默认进入待审批（应用层亦显式置 PENDING）。
ALTER TABLE sod_exception ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE sod_exception ALTER COLUMN approver DROP NOT NULL;
ALTER TABLE sod_exception ALTER COLUMN approved_at DROP NOT NULL;
