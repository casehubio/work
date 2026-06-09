-- V35: Add tenancy_id to all runtime entities
ALTER TABLE work_item ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE work_item_template ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE audit_entry ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE work_item_note ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE work_item_link ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE work_item_spawn_group ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE work_item_schedule ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE work_item_relation ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE routing_cursor ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE label_definition ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE label_vocabulary ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE filter_rule ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';

-- Indexes
CREATE INDEX idx_work_item_tenancy ON work_item(tenancy_id);
CREATE INDEX idx_work_item_template_tenancy ON work_item_template(tenancy_id);
CREATE INDEX idx_audit_entry_tenancy ON audit_entry(tenancy_id);
CREATE INDEX idx_work_item_note_tenancy ON work_item_note(tenancy_id);
CREATE INDEX idx_work_item_link_tenancy ON work_item_link(tenancy_id);
CREATE INDEX idx_work_item_spawn_group_tenancy ON work_item_spawn_group(tenancy_id);
CREATE INDEX idx_work_item_schedule_tenancy ON work_item_schedule(tenancy_id);
CREATE INDEX idx_work_item_relation_tenancy ON work_item_relation(tenancy_id);
CREATE INDEX idx_label_definition_tenancy ON label_definition(tenancy_id);
CREATE INDEX idx_label_vocabulary_tenancy ON label_vocabulary(tenancy_id);
CREATE INDEX idx_filter_rule_tenancy ON filter_rule(tenancy_id);
CREATE INDEX idx_routing_cursor_tenancy ON routing_cursor(tenancy_id);

-- WorkItemTemplate: unique(name) → unique(name, tenancy_id)
ALTER TABLE work_item_template DROP CONSTRAINT IF EXISTS uq_work_item_template_name;
ALTER TABLE work_item_template ADD CONSTRAINT uq_work_item_template_name_tenant UNIQUE(name, tenancy_id);

-- RoutingCursor: PK (pool_hash) → PK (pool_hash, tenancy_id)
ALTER TABLE routing_cursor DROP CONSTRAINT IF EXISTS pk_routing_cursor;
ALTER TABLE routing_cursor ADD PRIMARY KEY (pool_hash, tenancy_id);
