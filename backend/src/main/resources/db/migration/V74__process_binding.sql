-- =============================================================
-- V74 · D1-8 §八 自定义工作流（H-06）：流程绑定 process_binding
-- =============================================================
-- 按 (object_type + org_id + condition) 绑定一个 Flowable 流程定义（process_def_key + version）。
-- condition 为声明式 JSON（{predicates:[{field,op,value}]}，AND；空=兜底默认），运行期按单据上下文匹配选流程。
-- 版本快照固化：单据【发起时】把选中的 process_def_key + version 记到单据/审批实例上，后续改绑定不影响在途单据。
-- 审批节点仍走 M8（ApprovalFlow/WorkflowService 的四元组与职责分离，不可自批）——本表只做"条件化选流程"，不绕权限。
-- 携 org_id + RLS，绑定本身也按组织隔离（org13 看不到 org12 的绑定，跨组织绑定永不命中）。
-- =============================================================

CREATE TABLE process_binding (
  id              BIGSERIAL    PRIMARY KEY,
  org_id          BIGINT       NOT NULL,                    -- 隔离锚点（同组织同 object_type 可多条按条件分流）
  object_type     VARCHAR(32)  NOT NULL,                    -- 单据类型：POLICY/RISK_ACCEPTANCE/ASSESSMENT/REMEDIATION/...
  name            VARCHAR(128) NOT NULL,                    -- 绑定名（便于识别）
  condition       TEXT         NOT NULL DEFAULT '{}',       -- 声明式 JSON：{predicates:[{field,op,value}]}；{} 或空谓词=兜底
  process_def_key VARCHAR(64)  NOT NULL,                    -- Flowable 流程定义 key
  process_version INT          NOT NULL DEFAULT 1,          -- 流程版本（快照固化用）
  seq             INT          NOT NULL DEFAULT 0,          -- 匹配优先级（小者先，同序按 id）
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE/RETIRED
  created_by      VARCHAR(64),
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_process_binding_obj ON process_binding(org_id, object_type, status, seq);

ALTER TABLE process_binding ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_process_binding_iso ON process_binding
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE ON process_binding TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE process_binding_id_seq TO grc_app;
