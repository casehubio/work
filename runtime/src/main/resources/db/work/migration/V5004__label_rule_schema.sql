-- #314: Unified label rule table (replaces work_item_filter, filter_rule, filter_chain)

DROP TABLE IF EXISTS filter_chain_work_item;
DROP TABLE IF EXISTS filter_chain;
DROP TABLE IF EXISTS work_item_filter;
DROP TABLE IF EXISTS filter_rule;

CREATE TABLE label_rule (
    id                   UUID PRIMARY KEY,
    tenancy_id           VARCHAR(255) NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    description          VARCHAR(500),
    condition_language   VARCHAR(20)  NOT NULL,
    condition_expression TEXT,
    actions_json         TEXT         NOT NULL DEFAULT '[]',
    trigger_events       VARCHAR(100) DEFAULT '',
    scope                VARCHAR(500),
    enabled              BOOLEAN      DEFAULT true,
    created_at           TIMESTAMP    NOT NULL
);
