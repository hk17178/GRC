-- =============================================================
-- V81 · 供应商评估过程文档留存（用户反馈：评估须有过程/依据可溯）
-- =============================================================
-- 为供应商评估记录补：评估依据（所用评估表单/标准/维度说明）+ 评估表单/报告原件附件
-- （原件字节 + sha256 固化，与制度/证据同款防篡改口径）。让"通过什么评估表单得出结果"可溯。
-- =============================================================

ALTER TABLE vendor_assessment ADD COLUMN basis      TEXT;          -- 评估依据/所用评估表单/维度说明
ALTER TABLE vendor_assessment ADD COLUMN doc_name   VARCHAR(255);  -- 评估表单/报告原件文件名
ALTER TABLE vendor_assessment ADD COLUMN doc_sha256 VARCHAR(64);   -- 原件 sha256（固化留档）
ALTER TABLE vendor_assessment ADD COLUMN doc_bytes  BYTEA;         -- 原件字节
