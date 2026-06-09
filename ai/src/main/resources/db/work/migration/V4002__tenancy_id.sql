-- Add tenancy_id to ai module tables

ALTER TABLE worker_skill_profile ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
CREATE INDEX idx_worker_skill_profile_tenancy ON worker_skill_profile(tenancy_id);

ALTER TABLE escalation_summary ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
CREATE INDEX idx_escalation_summary_tenancy ON escalation_summary(tenancy_id);
