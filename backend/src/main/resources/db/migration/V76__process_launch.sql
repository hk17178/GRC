-- =============================================================
-- V76 · D1-8 §八 H-06 接线：流程发起快照固化 process_launch
-- =============================================================
-- 单据【发起审批时】记录本次选中的 process_definition_key + version（+命中的 binding_id）到此表。
-- 后续改流程绑定不影响在途单据——此快照是发起时点的不可变固化记录（可按 biz_type+biz_id 回查）。
-- 携 org_id + RLS，按组织隔离。
-- =============================================================

CREATE TABLE process_launch (
  id                  BIGSERIAL    PRIMARY KEY,
  org_id              BIGINT       NOT NULL,                -- 隔离锚点
  biz_type            VARCHAR(48)  NOT NULL,                -- 单据类型（与 process_binding.object_type 同域）
  biz_id              BIGINT       NOT NULL,                -- 单据 id
  process_def_key     VARCHAR(64)  NOT NULL,                -- 本次发起实际使用的 Flowable 流程 key
  process_version     INT          NOT NULL,                -- 固化的流程版本
  binding_id          BIGINT,                              -- 命中的 process_binding（回落通用审批流时为空）
  process_instance_id VARCHAR(64)  NOT NULL,               -- Flowable 流程实例 id
  submitter           VARCHAR(64),
  launched_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_process_launch_biz ON process_launch(org_id, biz_type, biz_id);

ALTER TABLE process_launch ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_process_launch_iso ON process_launch
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT, UPDATE ON process_launch TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE process_launch_id_seq TO grc_app;
