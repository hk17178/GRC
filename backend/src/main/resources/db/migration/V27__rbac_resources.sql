-- =============================================================
-- V27 · 功能级 RBAC 权限模型（增强③ R2）
-- =============================================================
-- resource       权限资源（菜单 MENU + 按钮/动作 ACTION），集团级全局字典，不挂 org/RLS。
-- role_resource  角色×资源 → 级别(RW/RO/HIDDEN)，权限矩阵，全局，不挂 RLS。
-- role 加 superadmin 标记：ORG_ADMIN/PLATFORM_ADMIN 对全部资源视为 RW（解析时短路，免铺全量行）。
-- 角色集团统一定义、按组织授予用户(user_role_org)。默认拒绝：未授权资源 = HIDDEN。
-- =============================================================

ALTER TABLE role ADD COLUMN IF NOT EXISTS superadmin BOOLEAN NOT NULL DEFAULT false;

-- ---------- 资源字典 ----------
CREATE TABLE resource (
  id          BIGSERIAL    PRIMARY KEY,
  code        VARCHAR(64)  NOT NULL UNIQUE,     -- 菜单=navKey；动作=menu.action
  name        VARCHAR(64)  NOT NULL,
  type        VARCHAR(8)   NOT NULL,            -- MENU / ACTION
  parent_menu VARCHAR(64),                      -- ACTION 归属菜单(navKey)
  sort        INT          NOT NULL DEFAULT 0
);
GRANT SELECT ON resource TO grc_app;

-- ---------- 角色×资源 权限矩阵 ----------
CREATE TABLE role_resource (
  role_id       BIGINT      NOT NULL REFERENCES role(id),
  resource_code VARCHAR(64) NOT NULL REFERENCES resource(code),
  level         VARCHAR(8)  NOT NULL,           -- RW / RO / HIDDEN
  PRIMARY KEY (role_id, resource_code)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON role_resource TO grc_app;

-- ---------- 种子：菜单资源（对齐前端 navKey）----------
INSERT INTO resource (code, name, type, sort) VALUES
  ('dashboard','合规态势','MENU',1),
  ('todo','我的待办','MENU',2),
  ('extaudit','外部审计','MENU',3),
  ('audit','内部审计','MENU',4),
  ('risk','风险评估','MENU',5),
  ('law','法规跟踪','MENU',6),
  ('regaffairs','监管事项','MENU',7),
  ('obligation','合规清单','MENU',8),
  ('policy','制度发布','MENU',9),
  ('ai','AI智能问答','MENU',10),
  ('vendor','第三方供应商','MENU',11),
  ('org','组织与资产','MENU',12),
  ('notify','通知中心','MENU',13),
  ('aimodel','模型接入','MENU',14),
  ('perm','权限与审批','MENU',15),
  ('approvalflow','审批流配置','MENU',16),
  ('board','看板与留痕','MENU',17),
  ('feedback','建议与反馈','MENU',18),
  ('settings','系统设置','MENU',19);

-- ---------- 种子：动作资源（按钮级，覆盖主要写操作；后续 R3/R4 扩展）----------
INSERT INTO resource (code, name, type, parent_menu, sort) VALUES
  ('risk.create','发起评估','ACTION','risk',1),
  ('risk.closeFinding','关闭/验证风险发现','ACTION','risk',2),
  ('risk.requestAccept','申请风险接受','ACTION','risk',3),
  ('risk.approveAccept','审批风险接受','ACTION','risk',4),
  ('policy.create','新建制度','ACTION','policy',1),
  ('policy.submit','提交评审','ACTION','policy',2),
  ('policy.decide','审批/驳回制度','ACTION','policy',3),
  ('policy.signoff','签署/废止制度','ACTION','policy',4),
  ('regfiling.create','登记报送','ACTION','regaffairs',1),
  ('regfiling.submit','提交评审','ACTION','regaffairs',2),
  ('regfiling.approve','审批报送','ACTION','regaffairs',3),
  ('vendor.create','登记供应商','ACTION','vendor',1),
  ('vendor.assess','供应商评估','ACTION','vendor',2),
  ('vendor.activate','启用供应商','ACTION','vendor',3),
  ('obligation.create','登记义务','ACTION','obligation',1),
  ('obligation.fulfill','标记落实','ACTION','obligation',2),
  ('approvalflow.save','保存审批流','ACTION','approvalflow',1),
  ('approvalflow.publish','发布审批流','ACTION','approvalflow',2);

-- ---------- 种子：角色 ----------
INSERT INTO role (code, name, superadmin) VALUES
  ('PLATFORM_ADMIN','平台超级管理员',true),
  ('ORG_ADMIN','子公司超级管理员',true),
  ('RISK_OFFICER','风险专员',false);

-- ---------- 种子：RISK_OFFICER 权限矩阵（演示三级：RW/RO/HIDDEN）----------
-- 风险相关 RW；多数 RO；管理类未授权=HIDDEN（默认拒绝）。
INSERT INTO role_resource (role_id, resource_code, level)
SELECT r.id, x.code, x.level FROM role r,
  (VALUES
    ('dashboard','RO'),('todo','RO'),('notify','RO'),('board','RO'),
    ('risk','RW'),('risk.create','RW'),('risk.closeFinding','RW'),('risk.requestAccept','RW'),('risk.approveAccept','RW'),
    ('law','RO'),('obligation','RO'),('regaffairs','RO'),('vendor','RO'),('policy','RO'),('ai','RO')
  ) AS x(code, level)
WHERE r.code = 'RISK_OFFICER';

-- ---------- 种子：用户授角色（集团统一角色 × 按组织授予）----------
-- group_admin@集团 → 平台超管；pay_user@支付 → 子公司超管；cf_user@消金 → 风险专员（受限）
INSERT INTO user_role_org (org_id, user_id, role_id, active, granted_by, granted_at)
SELECT u.org_id, u.id, r.id, true, 'system', now()
FROM app_user u JOIN role r ON r.code = 'PLATFORM_ADMIN'
WHERE u.username = 'group_admin';

INSERT INTO user_role_org (org_id, user_id, role_id, active, granted_by, granted_at)
SELECT u.org_id, u.id, r.id, true, 'system', now()
FROM app_user u JOIN role r ON r.code = 'ORG_ADMIN'
WHERE u.username = 'pay_user';

INSERT INTO user_role_org (org_id, user_id, role_id, active, granted_by, granted_at)
SELECT u.org_id, u.id, r.id, true, 'system', now()
FROM app_user u JOIN role r ON r.code = 'RISK_OFFICER'
WHERE u.username = 'cf_user';
