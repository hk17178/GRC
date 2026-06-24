-- =============================================================
-- V21 · 合规清单（Phase B：合规义务库 + 落实追踪）
-- =============================================================
-- obligation：一条合规义务（来源/要求/责任部门/期限/落实状态/证据）。
-- 落实闭环：FULFILLED 须留证据（应用层强制，见 ObligationService.fulfill）。
-- 与业务表同口径：携 org_id、ENABLE RLS 按 visible_orgs 裁剪（含写入校验）。
-- =============================================================

CREATE TABLE obligation (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,                             -- 隔离锚点
  code         VARCHAR(64)  NOT NULL,                             -- 义务编号（组织内唯一）
  title        VARCHAR(256) NOT NULL,
  source_ref   VARCHAR(128),                                      -- 来源（法规/标准编号，软引用）
  category     VARCHAR(64),
  requirement  TEXT,                                              -- 具体要求
  owner_dept   VARCHAR(64),                                       -- 责任部门
  due_date     DATE,                                              -- 落实期限
  status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',           -- PENDING/IN_PROGRESS/FULFILLED/NON_COMPLIANT
  evidence     TEXT,                                              -- 落实证据（FULFILLED 必填）
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_obligation_org_code UNIQUE (org_id, code)
);
CREATE INDEX idx_obligation_status ON obligation(org_id, status);

-- ---------- 组织隔离（按 visible_orgs 裁剪，含写入校验） ----------
ALTER TABLE obligation ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_obligation_iso ON obligation
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

-- ---------- 运行期账号授权 ----------
GRANT SELECT, INSERT, UPDATE ON obligation TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE obligation_id_seq TO grc_app;
