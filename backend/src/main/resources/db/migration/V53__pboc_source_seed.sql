-- =============================================================
-- V53 · 人行法规追踪源开箱配置（UAT 三轮 #6）
-- 内置两个中国人民银行（条法司）HTTP 追踪源：国家法律 / 部门规章。
-- 选择器已按 pbc.gov.cn 实际页面结构核实（列表项 font.newslist_style，日期 span.hui12）；
-- 站点改版时在「法规跟踪 · 追踪源」界面调整 config 即可，无须改码。
-- 种到集团组织（org 1），启用 + DAILY（由定时调度自动抓取，亦可「立即抓取」）。
-- =============================================================

INSERT INTO regulation_source (org_id, name, source_type, url, config, frequency, enabled)
VALUES
  (1, '中国人民银行 · 国家法律（条法司）', 'HTTP',
   'http://www.pbc.gov.cn/tiaofasi/144941/144951/index.html',
   '{"listSelector":"font.newslist_style","titleSelector":"a","linkSelector":"a","dateSelector":null,"issuer":"中国人民银行","category":"国家法律"}',
   'DAILY', true),
  (1, '中国人民银行 · 部门规章（条法司）', 'HTTP',
   'http://www.pbc.gov.cn/tiaofasi/144941/144957/index.html',
   '{"listSelector":"font.newslist_style","titleSelector":"a","linkSelector":"a","dateSelector":null,"issuer":"中国人民银行","category":"部门规章"}',
   'DAILY', true);
