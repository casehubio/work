-- Federation fields: shadow WorkItem origin tracking
ALTER TABLE work_item ADD COLUMN origin_service_id VARCHAR(255);
ALTER TABLE work_item ADD COLUMN origin_work_item_id UUID;
ALTER TABLE work_item ADD COLUMN origin_version BIGINT;
CREATE UNIQUE INDEX idx_work_item_origin ON work_item (origin_service_id, origin_work_item_id);
