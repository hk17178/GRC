-- =============================================================
-- V77 · B30 数据分级引擎：敏感数据访问留痕 sensitive_access_log
-- =============================================================
-- 三级访问控制（PUBLIC/INTERNAL/SENSITIVE）：当调用方经自定义视图/报表读取被标记
-- is_sensitive 的自定义字段时，分级引擎按其数据密级（clearance）决定明文放行或脱敏，
-- 并把「谁、何时、读了哪个对象的哪些敏感字段、放行还是脱敏」记入本表。
--
-- 携 org_id + RLS：留痕锚定操作者主可见组织（其审计域），子公司审计员只见本域的敏感访问。
-- 与 notify_send_log 同口径（服务层 native SQL 写入），但本表【有 RLS】——它记录的是
-- 谁访问了敏感数据，属组织内审计资产，不可跨子公司泄露。
-- =============================================================

CREATE TABLE sensitive_access_log (
  id          BIGSERIAL    PRIMARY KEY,
  org_id      BIGINT       NOT NULL,                 -- 隔离锚点（操作者主可见组织）
  username    VARCHAR(64)  NOT NULL,                 -- 访问者
  object_type VARCHAR(32)  NOT NULL,                 -- 宿主对象类型（本期 ASSET）
  field_keys  VARCHAR(512) NOT NULL,                 -- 命中的敏感字段列（逗号分隔 ext.<key>）
  row_count   INT          NOT NULL DEFAULT 0,       -- 本次访问返回的行数
  clearance   VARCHAR(16)  NOT NULL,                 -- 访问者数据密级（INTERNAL/SENSITIVE）
  action      VARCHAR(16)  NOT NULL,                 -- GRANTED（明文放行）/ MASKED（脱敏）
  accessed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_sensitive_access_org ON sensitive_access_log(org_id, id DESC);

ALTER TABLE sensitive_access_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_sensitive_access_iso ON sensitive_access_log
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));

GRANT SELECT, INSERT ON sensitive_access_log TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE sensitive_access_log_id_seq TO grc_app;
