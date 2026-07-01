ALTER TABLE work_item_spawn_group ADD COLUMN group_status VARCHAR(15);

UPDATE work_item_spawn_group SET group_status = CASE
    WHEN policy_triggered = true AND completed_count >= required_count THEN 'COMPLETED'
    WHEN policy_triggered = true AND completed_count < required_count THEN 'REJECTED'
    WHEN required_count IS NOT NULL THEN 'IN_PROGRESS'
    ELSE NULL
END;
