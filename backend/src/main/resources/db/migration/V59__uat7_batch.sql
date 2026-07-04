-- =============================================================
-- UAT 七轮批次（评估报告 EVAL-2026-07-04 排期 7-1 ~ 7-13 的 DDL 部分）：
--  1) 7-2 证据通用挂接：evidence 增加 报送/重大事件 外键列，回执证据可入证据库
--     （B2 红线：报送了结必须挂回执证据；B3：重大事件同理）
--  2) 7-2 重大事件：法定报送时限 + 监管确认时间（ACKNOWLEDGED 段）
--  3) 7-12 签批细粒度权限资源 risk.signoff（超管天然通过；审批人角色显式授予；
--     风险专员默认无 → 普通安全员不能做管理层签批）
-- =============================================================

-- 1) 证据挂接报送/重大事件
ALTER TABLE evidence ADD COLUMN filing_id   BIGINT;
ALTER TABLE evidence ADD COLUMN incident_id BIGINT;
CREATE INDEX idx_evidence_filing   ON evidence(filing_id);
CREATE INDEX idx_evidence_incident ON evidence(incident_id);

-- 2) 重大事件：法定报送时限（支付机构 2h/24h 级要求折算到日粒度先行）+ 监管确认段
ALTER TABLE major_incident_report ADD COLUMN report_deadline DATE;
ALTER TABLE major_incident_report ADD COLUMN acknowledged_at TIMESTAMPTZ;

-- 3) 管理层签批细粒度资源：先登记资源字典（role_resource 有外键），再授予审批人（CHECKER）；
--    其余角色默认 HIDDEN=拒绝（普通安全员不能做管理层签批）
INSERT INTO resource (code, name, type, parent_menu, sort)
VALUES ('risk.signoff', '管理层签批', 'ACTION', 'risk', 65);
INSERT INTO role_resource (role_id, resource_code, level)
SELECT r.id, 'risk.signoff', 'RW' FROM role r WHERE r.code = 'CHECKER';
