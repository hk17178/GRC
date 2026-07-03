-- =============================================================
-- V54 · 审计报告模板管理（UAT 三轮 #2）
-- 报告正文骨架模板：生成报告草稿时可选模板作骨架，系统组稿（发现五要素/整改台账）附后。
-- 内置两份（org 1，可克隆语义=直接复制正文另存）：
--  ① 通用内部审计报告模板（IIA 通行结构）
--  ② 个人信息保护合规审计报告模板（对标《个人信息保护合规审计管理办法》，
--     国家网信办 2025 年 2 月公布、2025 年 5 月 1 日施行，及其配套审计指引要点）
-- =============================================================

CREATE TABLE audit_report_template (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL REFERENCES org(id),   -- 隔离锚点
  name        VARCHAR(256) NOT NULL,
  category    VARCHAR(64),                                -- 通用内审 / 个人信息保护 / 等保 / …
  content     TEXT         NOT NULL,                      -- 报告正文骨架（章节占位）
  enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_by  VARCHAR(64),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_report_template_org ON audit_report_template(org_id);

ALTER TABLE audit_report_template ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_audit_report_template_iso ON audit_report_template
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE, DELETE ON audit_report_template TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE audit_report_template_id_seq TO grc_app;

INSERT INTO audit_report_template (org_id, name, category, content, created_by) VALUES
(1, '通用内部审计报告模板', '通用内审',
'一、审计概况
（审计项目、审计依据、审计范围与期间、审计组组成、审计方式方法）

二、被审计单位基本情况
（组织与职责、业务概况、与审计事项相关的内控制度）

三、审计评价
（总体评价：内控健全性与有效性；本节结论应与「审计意见」分级一致）

四、审计发现与建议
（按重要性排序，每项含：现状、标准、原因、影响、建议、管理层回应——系统组稿附录已按五要素列出，可整理后并入本节）

五、整改要求
（整改责任、时限与验证安排）

六、其他需要说明的事项
（审计范围受限、后续审计安排等）', 'platform'),
(1, '个人信息保护合规审计报告模板', '个人信息保护',
'一、审计基本情况
（审计性质：自行审计/受托专业机构审计；审计依据：《个人信息保护法》《个人信息保护合规审计管理办法》（2025 年 5 月 1 日施行）及配套指引；审计范围与期间；审计组）

二、被审计人个人信息处理活动概况
（处理目的与方式、处理的个人信息种类与规模、是否属于处理 1000 万人以上个人信息的处理者、共同处理/委托处理/对外提供情况、跨境提供情况）

三、合规审计过程与方法
（访谈、查验制度与记录、技术核验、抽样情况）

四、合规情况评价（对照办法及指引逐项）
1. 处理规则与告知同意（单独同意情形核验）
2. 处理者义务履行（分类管理、安全技术措施、权限与培训）
3. 个人权利保障（查阅复制、更正删除、可携带、撤回同意渠道）
4. 委托处理、共同处理与对外提供的合规约束
5. 跨境提供合规路径（安全评估/认证/标准合同）
6. 敏感个人信息与未成年人个人信息特别保护
7. 个人信息保护影响评估（PIA）执行情况
8. 安全事件应急与通知义务
9. 个人信息保护负责人与内部管理机构履职
10. 自动化决策、大型平台特别义务（如适用）

五、发现的问题及整改建议
（问题描述、违反条款、风险程度、整改建议与时限）

六、审计结论
（合规/基本合规/存在重大合规风险；须与审计意见分级一致）

七、附件
（抽样清单、证据索引、被审计人确认意见）', 'platform');
