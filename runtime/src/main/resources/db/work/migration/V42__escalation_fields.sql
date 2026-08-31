ALTER TABLE work_item ADD COLUMN escalation_on_expiry VARCHAR(255);
ALTER TABLE work_item ADD COLUMN escalation_on_claim_deadline VARCHAR(255);
ALTER TABLE work_item ADD COLUMN escalation_deadline VARCHAR(32);
ALTER TABLE work_item ADD COLUMN escalation_generate_summary BOOLEAN;
