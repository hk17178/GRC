-- =============================================================
-- V68 · 收口批二期（B36 / M8-2 治理角色基础集补齐至 20）
-- =============================================================
-- 现有 7 个（V7 的 MAKER/CHECKER/RISK_OWNER/AUDITOR + V27 的 PLATFORM_ADMIN/
-- ORG_ADMIN/RISK_OFFICER）。此处补齐支付机构常见治理角色至 20，便于 UAT 演示
-- 按岗授权。仅角色定义（code/name），权限矩阵按需在权限管理中分配；superadmin 默认 false。
-- code 唯一，ON CONFLICT DO NOTHING 幂等（重复执行不报错）。
-- =============================================================

INSERT INTO role (code, name) VALUES
  ('COMPLIANCE_OFFICER',    '合规负责人'),
  ('COMPLIANCE_SPECIALIST', '合规专员'),
  ('AUDIT_MANAGER',         '内审负责人'),
  ('CISO',                  '信息安全负责人'),
  ('SEC_OPS',               '安全运营管理员'),
  ('DPO',                   '数据保护官'),
  ('AML_OFFICER',           '反洗钱专员'),
  ('LEGAL_COUNSEL',         '法务专员'),
  ('POLICY_ADMIN',          '制度管理员'),
  ('ASSET_ADMIN',           '资产管理员'),
  ('BIZ_DEPT_HEAD',         '业务部门负责人'),
  ('MANAGEMENT',            '管理层（签批）'),
  ('READONLY_OBSERVER',     '只读观察员')
ON CONFLICT (code) DO NOTHING;
