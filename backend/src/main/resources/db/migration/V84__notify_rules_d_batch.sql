-- =============================================================
-- V84 · 通知中心 D 批：四类新数据源的默认规则（用户讨论结论）
-- =============================================================
-- 与 NotifyRuleEngine 新增 source 对齐；集团(org 1)各一条默认启用规则，运营可在通知中心停用/改模板。
-- 约定同 V58：模板变量一律 {变量名}（不得出现 美元符+花括号，否则 Flyway 当占位符解析报错）。
--   D1 REG_CHANGE      —— 法规变更影响预警（登记变更时已把关联制度置需重评，本规则负责通知）
--   D3 ACCOUNT_LOCKED  —— 账号连续登录失败被锁定，供安全管理员感知
--   D3 UAR_OVERDUE     —— 权限访问复核（UAR）超期未完成
--   D4 COMPLIANCE_DIGEST —— 周期合规简报（逾期/KRI/需重评 汇总，按 ISO 周去重每周一条）
-- 注：制度复审/资质证书/监管报送/等保测评 等到期提醒由 ExpiryScanService 系统级统一产出，不在此重复。
-- =============================================================

INSERT INTO notify_config (org_id, kind, name, detail, enabled) VALUES
  (1, 'RULE', '法规变更影响预警',
   '{"source":"REG_CHANGE","days":7,"channel":"INBOX","template":"法规「{标题}」发生{变更类型}，{制度数} 项关联制度需重评，请及时复核符合度。"}', TRUE),
  (1, 'RULE', '账号锁定告警',
   '{"source":"ACCOUNT_LOCKED","days":0,"channel":"INBOX","template":"账号「{账号}」因连续登录失败已被锁定（解锁时间 {解锁时间}），请安全管理员核查是否异常。"}', TRUE),
  (1, 'RULE', '访问复核超期提醒',
   '{"source":"UAR_OVERDUE","days":7,"channel":"INBOX","template":"{周期} 访问复核已超期 {超期天数} 天未完成（审阅人 {审阅人}），请尽快复核处置。"}', TRUE),
  (1, 'RULE', '周期合规简报',
   '{"source":"COMPLIANCE_DIGEST","days":0,"channel":"INBOX","template":"【{周} 合规简报】整改逾期 {整改逾期} 项、KRI 严重 {KRI严重} 项、需重评制度 {待复审制度} 项，请关注处理。"}', TRUE);
