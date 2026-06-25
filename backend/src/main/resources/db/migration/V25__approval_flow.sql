-- =============================================================
-- V25 · 可配置审批流（P1：按组织×业务类型自定义审批链）
-- =============================================================
-- 审批流由可视化画布(graph_json)定义，发布时编译成 BPMN 部署给 Flowable 执行。
--   approval_flow      ：流程定义（每组织每业务类型一套，仅一条 ACTIVE）。
--   approval_instance  ：运行实例（映射到 Flowable 流程实例）。
--   approval_task_log  ：每步审批决定（审计 + 防篡改哈希链来源）。
-- 三表均携 org_id + ENABLE RLS 按 visible_orgs 裁剪（含写入校验），与业务表同口径。
-- =============================================================

-- ---------- 流程定义 ----------
CREATE TABLE approval_flow (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL,                       -- 隔离锚点（各子公司/集团各配各的）
  biz_type    VARCHAR(32)  NOT NULL,                       -- POLICY_PUBLISH/RISK_ACCEPT/SOD_EXCEPTION/REG_FILING…
  name        VARCHAR(128) NOT NULL,
  version     INT          NOT NULL DEFAULT 1,
  status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',       -- DRAFT/ACTIVE/RETIRED
  graph_json  TEXT,                                        -- 画布源（节点+连线+属性）
  bpmn_key    VARCHAR(64),                                 -- 发布后部署的 Flowable 流程 key
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE approval_flow ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_approval_flow_iso ON approval_flow
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON approval_flow TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE approval_flow_id_seq TO grc_app;

-- 同组织同业务类型仅允许一条 ACTIVE（部分唯一索引）
CREATE UNIQUE INDEX uk_approval_flow_active ON approval_flow (org_id, biz_type) WHERE status = 'ACTIVE';

-- ---------- 运行实例 ----------
CREATE TABLE approval_instance (
  id                  BIGSERIAL   PRIMARY KEY,
  org_id              BIGINT      NOT NULL,
  flow_id             BIGINT      NOT NULL,
  flow_version        INT         NOT NULL,
  biz_type            VARCHAR(32) NOT NULL,
  biz_id              BIGINT      NOT NULL,
  process_instance_id VARCHAR(64),                         -- Flowable 流程实例 id
  status              VARCHAR(16) NOT NULL DEFAULT 'RUNNING', -- RUNNING/APPROVED/REJECTED
  submitter           VARCHAR(64),
  started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at            TIMESTAMPTZ
);

ALTER TABLE approval_instance ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_approval_instance_iso ON approval_instance
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE ON approval_instance TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE approval_instance_id_seq TO grc_app;
CREATE INDEX idx_approval_instance_biz ON approval_instance (biz_type, biz_id);

-- ---------- 审批决定流水（审计 + 哈希链来源） ----------
CREATE TABLE approval_task_log (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL,
  instance_id BIGINT       NOT NULL,
  node_key    VARCHAR(64),
  node_name   VARCHAR(128),
  approver    VARCHAR(64),
  decision    VARCHAR(16),                                 -- APPROVE/REJECT/ESCALATE
  comment     TEXT,
  decided_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE approval_task_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_approval_task_log_iso ON approval_task_log
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT ON approval_task_log TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE approval_task_log_id_seq TO grc_app;
CREATE INDEX idx_approval_task_log_instance ON approval_task_log (instance_id);
