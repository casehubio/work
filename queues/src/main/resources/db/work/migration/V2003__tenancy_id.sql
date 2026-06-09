-- Add tenancy_id to queues module tables

ALTER TABLE work_item_queue_state ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
CREATE INDEX idx_work_item_queue_state_tenancy ON work_item_queue_state(tenancy_id);

ALTER TABLE queue_view ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
CREATE INDEX idx_queue_view_tenancy ON queue_view(tenancy_id);

ALTER TABLE work_item_filter ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
CREATE INDEX idx_work_item_filter_tenancy ON work_item_filter(tenancy_id);

ALTER TABLE filter_chain ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
CREATE INDEX idx_filter_chain_tenancy ON filter_chain(tenancy_id);

ALTER TABLE work_item_queue_membership ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
CREATE INDEX idx_work_item_queue_membership_tenancy ON work_item_queue_membership(tenancy_id);
