-- =============================================================
-- UAT 八轮批次（评估报告排期 8-1 ~ 8-12 的 DDL 部分）：
--  1) 8-1 通知外推发送留痕：notify_send_log（与 reminder_dispatch_log 同口径，内核写入，无 RLS）
--  2) 8-3 合规清单举证链：obligation_link（义务 → 制度/控制/评估/审计/证据 的关联，RLS）
--  3) 8-6 CR-003 身份数据模型先行：app_user 增 identity_source/domain_id/platform_disabled
--  4) 8-11 风险直登：risk_finding.assessment_id 可空 + 来源字段（事件/漏洞/审计/手工）
-- =============================================================

-- 1) 通知发送留痕（企微 Webhook 等通道外推的成功/失败记录）
CREATE TABLE notify_send_log (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,
  channel_type VARCHAR(16)  NOT NULL,              -- WECOM / FEISHU / EMAIL / SMS
  target       VARCHAR(512) NOT NULL,              -- webhook 地址（日志内截尾脱敏由展示层负责）
  message      TEXT,
  success      BOOLEAN      NOT NULL,
  error        VARCHAR(512),                       -- 失败原因（成功为 NULL）
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT ON notify_send_log TO grc_app;
GRANT USAGE ON SEQUENCE notify_send_log_id_seq TO grc_app;

-- 2) 义务举证链：一条义务可挂多类依据对象，满足状态由链上对象派生
CREATE TABLE obligation_link (
  id            BIGSERIAL   PRIMARY KEY,
  org_id        BIGINT      NOT NULL,
  obligation_id BIGINT      NOT NULL,
  ref_type      VARCHAR(16) NOT NULL,              -- POLICY / CONTROL / ASSESSMENT / AUDIT / EVIDENCE
  ref_id        BIGINT      NOT NULL,
  note          VARCHAR(256),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (obligation_id, ref_type, ref_id)
);
CREATE INDEX idx_obligation_link_ob ON obligation_link(obligation_id);
ALTER TABLE obligation_link ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_obligation_link_iso ON obligation_link
  USING (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON obligation_link TO grc_app;
GRANT USAGE ON SEQUENCE obligation_link_id_seq TO grc_app;

-- 3) CR-003 身份数据模型先行（真实 AD/LDAP 对接留联测；先把账号主键结构立起来避免后期迁移）
ALTER TABLE app_user ADD COLUMN identity_source   VARCHAR(16) NOT NULL DEFAULT 'LOCAL';  -- LOCAL / AD
ALTER TABLE app_user ADD COLUMN domain_id         BIGINT;                                -- AD 域标识（本地账号为空）
ALTER TABLE app_user ADD COLUMN platform_disabled BOOLEAN     NOT NULL DEFAULT FALSE;    -- 平台侧独立禁用位
-- 域内用户名唯一（本地账号沿用既有 username 唯一约束语义）
CREATE UNIQUE INDEX uq_app_user_domain_username ON app_user(domain_id, username) WHERE domain_id IS NOT NULL;

-- 4) 风险直登登记册：发现可不挂评估（事件/漏洞驱动的日常风险），带来源标注
ALTER TABLE risk_finding ALTER COLUMN assessment_id DROP NOT NULL;
ALTER TABLE risk_finding ADD COLUMN source VARCHAR(24);  -- EVENT / VULN / AUDIT / MANUAL（评估内生成为 NULL）
