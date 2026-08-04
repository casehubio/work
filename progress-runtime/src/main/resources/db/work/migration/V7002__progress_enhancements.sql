-- Progress model enhancements: visualisation mode and rollback policy (#329)
ALTER TABLE progress_instance ADD COLUMN visualisation_mode VARCHAR(50);
ALTER TABLE progress_instance ADD COLUMN rollback_policy VARCHAR(20);
