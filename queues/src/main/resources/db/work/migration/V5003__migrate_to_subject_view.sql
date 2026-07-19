-- V5003: migrate queue_view and work_item_queue_membership to platform subject_view tables
-- Platform V5000+V5001 create subject_view and view_membership tables.
-- This migration copies data from the old work-queues tables (preserving UUIDs)
-- and drops the old tables.

-- 1. Copy queue_view data to subject_view (preserving UUIDs for queue_snapshot FK)
INSERT INTO subject_view (id, name, tenancy_id, label_pattern, scope,
    sort_field, sort_direction, created_at, additional_conditions)
SELECT id, name, tenancy_id, label_pattern, scope,
    sort_field, sort_direction, created_at, additional_conditions
FROM queue_view;

-- 2. Copy work_item_queue_membership data to view_membership
INSERT INTO view_membership (subject_id, view_id, view_name)
SELECT work_item_id, queue_view_id, queue_name
FROM work_item_queue_membership;

-- 3. Repoint queue_snapshot FK from queue_view to subject_view
ALTER TABLE queue_snapshot DROP CONSTRAINT IF EXISTS fk_queue_snapshot_queue_view;
ALTER TABLE queue_snapshot ADD CONSTRAINT fk_queue_snapshot_subject_view
    FOREIGN KEY (queue_view_id) REFERENCES subject_view(id) ON DELETE CASCADE;

-- 4. Drop old tables (membership first due to potential FK ordering)
DROP TABLE IF EXISTS work_item_queue_membership;
DROP TABLE IF EXISTS queue_view;
