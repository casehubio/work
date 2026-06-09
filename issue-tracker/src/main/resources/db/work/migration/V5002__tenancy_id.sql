-- Add tenancy_id to issue-tracker module tables

ALTER TABLE work_item_issue_link ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
CREATE INDEX idx_work_item_issue_link_tenancy ON work_item_issue_link(tenancy_id);
