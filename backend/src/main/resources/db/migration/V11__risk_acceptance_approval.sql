-- =============================================================
-- V11 · 风险接受审批化（A2：风险接受走 Flowable 审批，与 CR-002 关闭门控衔接）
-- =============================================================
-- 背景：原 risk_acceptance 为「登记即放行」（accept 直接回填 finding.risk_acceptance_id）。
--       改为「申请→审批」两段式：申请置 PENDING（不放行），审批通过才回填放行凭据。
--       本迁移为 risk_acceptance 增审批所需列；以新增列方式兼容已有行（线上 grc-int-pg 已有数据）。
--
-- 列变更：
--   + requester   申请人（原 approver 仅有审批人语义，新增申请人）
--   + status      审批状态 PENDING/APPROVED/REJECTED（历史行视为已通过 → 默认回填 APPROVED）
--   + decided_at  审批落定时间
--   ~ approver    放宽为可空（PENDING 申请尚无审批人；通过/驳回时落定）
-- =============================================================

ALTER TABLE risk_acceptance ADD COLUMN requester VARCHAR(64);
-- 历史行（旧「登记即放行」语义）等同已通过，先以 APPROVED 回填既有行；
ALTER TABLE risk_acceptance ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'APPROVED';
-- 回填后把列默认改为 PENDING，使后续直插默认进入待审批（应用层亦会显式置 PENDING）。
ALTER TABLE risk_acceptance ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE risk_acceptance ADD COLUMN decided_at TIMESTAMPTZ;
-- 申请阶段尚无审批人，放宽 approver 可空（通过/驳回时由应用落定）。
ALTER TABLE risk_acceptance ALTER COLUMN approver DROP NOT NULL;
