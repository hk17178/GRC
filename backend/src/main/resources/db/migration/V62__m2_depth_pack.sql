-- =============================================================
-- V62 · M2 深度包一期（B47 资产合规属性深化 + C3 风险矩阵上收）
-- =============================================================

-- 1) B47：资产合规属性深化——等保定级/测评到期/CIA 三性/网络区域。
--    等保三级及以上系统须每年测评，到期日进内核到期扫描（30/7/0 天提醒）。
ALTER TABLE asset ADD COLUMN mlps_level      INT;            -- 等保定级 1~4（未定级为 NULL）
ALTER TABLE asset ADD COLUMN mlps_review_due DATE;           -- 等保测评到期日
ALTER TABLE asset ADD COLUMN cia_rating      VARCHAR(16);    -- CIA 三性评级（如 3-3-2）
ALTER TABLE asset ADD COLUMN network_zone    VARCHAR(64);    -- 网络区域（生产核心区/DMZ/办公网等）

ALTER TABLE asset ADD CONSTRAINT ck_asset_mlps_level CHECK (mlps_level IS NULL OR mlps_level BETWEEN 1 AND 4);

-- 2) C3：风险矩阵五级档位上收集团配置（单一事实源）。
--    此前 ScoringService（3/6/12/20/25）与 ATV 台账（4/8/12/16）两处硬编码且不一致，
--    统一为 ATV 历史口径（生产台账定级行为不变）。五级制为 V1.9 基线决策，
--    级数与档位锁定（editable=false）——调整走迁移+重启，不开放运行期改动。
INSERT INTO system_setting (org_id, setting_key, setting_value, value_type, category, description, editable) VALUES
  (1, 'risk.matrix.bands',
   '[{"max":4,"level":"VERY_LOW"},{"max":8,"level":"LOW"},{"max":12,"level":"MID"},{"max":16,"level":"HIGH"},{"max":25,"level":"VERY_HIGH"}]',
   'JSON', 'risk',
   '风险矩阵五级定级档位（可能性×影响 乘积上界→等级）——全平台唯一事实源，系统锁定项', FALSE);
