-- Add tenancy_id to notifications module tables

ALTER TABLE work_item_notification_rule ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
CREATE INDEX idx_work_item_notification_rule_tenancy ON work_item_notification_rule(tenancy_id);
