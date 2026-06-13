-- V36: Replace VocabularyScope enum with Path-based scope hierarchy (#236)
-- Converts scope column from enum strings to path strings,
-- drops the redundant owner_id column, and enforces uniqueness.

-- Widen scope column from VARCHAR(20) to VARCHAR(500) for path strings
ALTER TABLE label_vocabulary ALTER COLUMN scope TYPE VARCHAR(500);

-- Convert enum values to path strings:
-- GLOBAL (owner_id NULL) → '' (root path)
-- ORG/TEAM/PERSONAL → owner_id value (identity preserved, tier lost)
UPDATE label_vocabulary SET scope = '' WHERE scope = 'GLOBAL';
UPDATE label_vocabulary SET scope = COALESCE(owner_id, '') WHERE scope IN ('ORG', 'TEAM', 'PERSONAL');

-- Drop owner_id — subsumed by scope path
ALTER TABLE label_vocabulary DROP COLUMN owner_id;

-- One vocabulary per scope path per tenant (follows V35 uq_work_item_template_name_tenant pattern)
CREATE UNIQUE INDEX uq_label_vocabulary_scope_tenant ON label_vocabulary(scope, tenancy_id);
