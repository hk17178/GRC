-- =============================================================
-- V32 · 法规跟踪爬虫（权威信息源采集，替代手动登记）
-- =============================================================
-- regulation_source：可配置的"追踪源"。SAMPLE=内置示例源（不外联，演示用）；HTTP=按 URL+选择器抓取。
-- regulation_crawled：从源采集到的法规条目，按 (org, dedup_key) 去重。
-- 二表携 org_id + RLS，与业务表同口径。
-- =============================================================

CREATE TABLE regulation_source (
  id              BIGSERIAL    PRIMARY KEY,
  org_id          BIGINT       NOT NULL,
  name            VARCHAR(128) NOT NULL,                  -- 源名称（如"全国人大法律库"）
  source_type     VARCHAR(16)  NOT NULL DEFAULT 'SAMPLE', -- SAMPLE / HTTP
  url             TEXT,                                   -- HTTP 源的列表页地址
  config          TEXT,                                   -- HTTP 源的 CSS 选择器配置(JSON)
  frequency       VARCHAR(16)  NOT NULL DEFAULT 'DAILY',  -- 检测频率（展示用：DAILY/WEEKLY）
  enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
  status          VARCHAR(16)  NOT NULL DEFAULT 'OK',     -- OK / ERROR
  last_fetched_at TIMESTAMPTZ,
  last_hit_count  INT          NOT NULL DEFAULT 0,
  last_error      TEXT,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE regulation_source ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_regulation_source_iso ON regulation_source
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON regulation_source TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE regulation_source_id_seq TO grc_app;

CREATE TABLE regulation_crawled (
  id           BIGSERIAL    PRIMARY KEY,
  org_id       BIGINT       NOT NULL,
  source_id    BIGINT       NOT NULL,
  dedup_key    VARCHAR(256) NOT NULL,                     -- 去重键（url 或文号）
  title        TEXT         NOT NULL,
  doc_no       VARCHAR(128),                              -- 发文字号
  issuer       VARCHAR(128),                              -- 发布机关
  category     VARCHAR(64),                               -- 分类（体系/主题）
  publish_date DATE,
  url          TEXT,
  summary      TEXT,
  fetched_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE regulation_crawled ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_regulation_crawled_iso ON regulation_crawled
  USING      (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]))
  WITH CHECK (org_id = ANY (string_to_array(current_setting('app.visible_orgs', true), ',')::bigint[]));
GRANT SELECT, INSERT, UPDATE, DELETE ON regulation_crawled TO grc_app;
GRANT USAGE, SELECT ON SEQUENCE regulation_crawled_id_seq TO grc_app;
-- 同组织内同源去重
CREATE UNIQUE INDEX uk_regulation_crawled_dedup ON regulation_crawled (org_id, dedup_key);
