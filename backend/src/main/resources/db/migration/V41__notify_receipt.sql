-- =============================================================
-- V41 通知回执（M10 深度）
-- reminder_dispatch_log 加已读回执列：谁在何时确认收到该提醒。
-- 未读 read_by IS NULL；回执由 /api/workbench/notifications/{id}/ack 写入。
-- =============================================================
ALTER TABLE reminder_dispatch_log ADD COLUMN IF NOT EXISTS read_by VARCHAR(64);
ALTER TABLE reminder_dispatch_log ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;
