-- Consolidated V1 schema for casehub-work runtime
-- Merged from V1–V44, V3000–V3003, V5004 into final-state DDL.
-- Tables dropped during evolution (work_item_form_schema, work_item_notification_rule, filter_rule) are excluded.

-- ============================================================
-- work_item
-- ============================================================
CREATE TABLE IF NOT EXISTS work_item (
    id                            UUID            NOT NULL,
    title                         VARCHAR(500)    NOT NULL,
    description                   VARCHAR(4000),
    form_key                      VARCHAR(500),

    status                        VARCHAR(50)     NOT NULL,
    priority                      VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',

    -- Assignment
    assignee_id                   VARCHAR(255),
    owner                         VARCHAR(255),
    candidate_groups              VARCHAR(2000),
    candidate_users               VARCHAR(2000),
    required_capabilities         VARCHAR(2000),
    created_by                    VARCHAR(255),

    -- Delegation
    delegation_chain              VARCHAR(4000),
    delegation_decline_target     VARCHAR(10),
    prior_status                  VARCHAR(50),

    -- Payload / resolution
    payload                       TEXT,
    resolution                    TEXT,
    payload_type_name             VARCHAR(512),
    resolution_type_name          VARCHAR(512),

    -- Schemas snapshotted from template at instantiation
    input_data_schema             TEXT,
    output_data_schema            TEXT,

    -- Template provenance
    template_id                   UUID,
    template_version              BIGINT,
    permitted_outcomes            TEXT,
    outcome                       VARCHAR(255),

    -- Excluded users (conflict-of-interest)
    excluded_users                TEXT,

    -- Routing context
    candidate_scores              TEXT,
    routing_experiences           TEXT,

    -- Confidence (AI agent metadata)
    confidence_score              DOUBLE PRECISION,

    -- Scope
    scope                         VARCHAR(255),

    -- Caller reference
    caller_ref                    VARCHAR(512),

    -- Parent (threaded inbox)
    parent_id                     UUID,

    -- Claim SLA tracking
    accumulated_unclaimed_seconds BIGINT          NOT NULL DEFAULT 0,
    last_returned_to_pool_at      TIMESTAMP,

    -- Deadlines
    claim_deadline                TIMESTAMP,
    expires_at                    TIMESTAMP,
    follow_up_date                TIMESTAMP,

    -- Timestamps
    created_at                    TIMESTAMP       NOT NULL,
    updated_at                    TIMESTAMP       NOT NULL,
    assigned_at                   TIMESTAMP,
    started_at                    TIMESTAMP,
    completed_at                  TIMESTAMP,
    suspended_at                  TIMESTAMP,

    -- OCC
    version                       BIGINT          NOT NULL DEFAULT 0,

    -- Tenancy
    tenancy_id                    VARCHAR(255)    NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',

    CONSTRAINT pk_work_item PRIMARY KEY (id),
    CONSTRAINT fk_work_item_parent FOREIGN KEY (parent_id) REFERENCES work_item(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_work_item_status          ON work_item (status);
CREATE INDEX IF NOT EXISTS idx_work_item_assignee_id     ON work_item (assignee_id);
CREATE INDEX IF NOT EXISTS idx_work_item_expires_at      ON work_item (expires_at);
CREATE INDEX IF NOT EXISTS idx_work_item_claim_deadline  ON work_item (claim_deadline);
CREATE INDEX IF NOT EXISTS idx_work_item_follow_up_date  ON work_item (follow_up_date);
CREATE INDEX IF NOT EXISTS idx_work_item_parent_id       ON work_item (parent_id);
CREATE INDEX IF NOT EXISTS idx_work_item_tenancy         ON work_item (tenancy_id);
CREATE INDEX IF NOT EXISTS idx_work_item_caller_ref      ON work_item (caller_ref);

-- ============================================================
-- work_item_type (join table — typed paths per work item)
-- ============================================================
CREATE TABLE IF NOT EXISTS work_item_type (
    work_item_id UUID         NOT NULL REFERENCES work_item(id),
    path         VARCHAR(500) NOT NULL,
    CONSTRAINT uq_work_item_type UNIQUE (work_item_id, path)
);

CREATE INDEX IF NOT EXISTS idx_work_item_type_path ON work_item_type (path);

-- ============================================================
-- audit_entry (FK to work_item dropped — orphan audit entries allowed)
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_entry (
    id              UUID            NOT NULL,
    work_item_id    UUID            NOT NULL,
    event           VARCHAR(50)     NOT NULL,
    actor           VARCHAR(255),
    detail          TEXT,
    occurred_at     TIMESTAMP       NOT NULL,
    tenancy_id      VARCHAR(255)    NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',

    CONSTRAINT pk_audit_entry PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_entry_work_item_id  ON audit_entry (work_item_id);
CREATE INDEX IF NOT EXISTS idx_audit_entry_occurred_at   ON audit_entry (occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_actor               ON audit_entry (actor);
CREATE INDEX IF NOT EXISTS idx_audit_event               ON audit_entry (event);
CREATE INDEX IF NOT EXISTS idx_audit_actor_occurred_at   ON audit_entry (actor, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_entry_tenancy       ON audit_entry (tenancy_id);

-- ============================================================
-- work_item_label
-- ============================================================
CREATE TABLE IF NOT EXISTS work_item_label (
    work_item_id    UUID            NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    path            VARCHAR(500)    NOT NULL,
    persistence     VARCHAR(20)     NOT NULL,
    applied_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_wil_work_item_id ON work_item_label (work_item_id);
CREATE INDEX IF NOT EXISTS idx_wil_path         ON work_item_label (path);

-- ============================================================
-- label_vocabulary
-- ============================================================
CREATE TABLE IF NOT EXISTS label_vocabulary (
    id          UUID            PRIMARY KEY,
    scope       VARCHAR(500)    NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    tenancy_id  VARCHAR(255)    NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce'
);

CREATE INDEX IF NOT EXISTS idx_label_vocabulary_tenancy ON label_vocabulary (tenancy_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_label_vocabulary_scope_tenant ON label_vocabulary (scope, tenancy_id);

-- Seed: global vocabulary
INSERT INTO label_vocabulary (id, scope, name, tenancy_id)
VALUES ('00000000-0000-0000-0000-000000000001', '', 'Global', '278776f9-e1b0-46fb-9032-8bddebdcf9ce');

-- ============================================================
-- label_definition
-- ============================================================
CREATE TABLE IF NOT EXISTS label_definition (
    id              UUID            PRIMARY KEY,
    path            VARCHAR(500)    NOT NULL,
    vocabulary_id   UUID            NOT NULL REFERENCES label_vocabulary(id) ON DELETE CASCADE,
    description     VARCHAR(1000),
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    tenancy_id      VARCHAR(255)    NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce'
);

CREATE INDEX IF NOT EXISTS idx_ld_vocabulary_id        ON label_definition (vocabulary_id);
CREATE INDEX IF NOT EXISTS idx_ld_path                 ON label_definition (path);
CREATE INDEX IF NOT EXISTS idx_label_definition_tenancy ON label_definition (tenancy_id);

-- Seed: common global labels
INSERT INTO label_definition (id, path, vocabulary_id, description, created_by, created_at, tenancy_id) VALUES
('00000000-0000-0000-0001-000000000001', 'intake',             '00000000-0000-0000-0000-000000000001', 'Newly submitted, awaiting triage',  'system', CURRENT_TIMESTAMP, '278776f9-e1b0-46fb-9032-8bddebdcf9ce'),
('00000000-0000-0000-0001-000000000002', 'intake/triage',      '00000000-0000-0000-0000-000000000001', 'Actively being triaged',            'system', CURRENT_TIMESTAMP, '278776f9-e1b0-46fb-9032-8bddebdcf9ce'),
('00000000-0000-0000-0001-000000000003', 'priority/high',      '00000000-0000-0000-0000-000000000001', 'High priority item',                'system', CURRENT_TIMESTAMP, '278776f9-e1b0-46fb-9032-8bddebdcf9ce'),
('00000000-0000-0000-0001-000000000004', 'priority/critical',  '00000000-0000-0000-0000-000000000001', 'Critical priority item',            'system', CURRENT_TIMESTAMP, '278776f9-e1b0-46fb-9032-8bddebdcf9ce'),
('00000000-0000-0000-0001-000000000005', 'legal',              '00000000-0000-0000-0000-000000000001', 'Legal domain work',                 'system', CURRENT_TIMESTAMP, '278776f9-e1b0-46fb-9032-8bddebdcf9ce'),
('00000000-0000-0000-0001-000000000006', 'legal/contracts',    '00000000-0000-0000-0000-000000000001', 'Contract review',                   'system', CURRENT_TIMESTAMP, '278776f9-e1b0-46fb-9032-8bddebdcf9ce'),
('00000000-0000-0000-0001-000000000007', 'legal/compliance',   '00000000-0000-0000-0000-000000000001', 'Compliance review',                 'system', CURRENT_TIMESTAMP, '278776f9-e1b0-46fb-9032-8bddebdcf9ce');

-- ============================================================
-- work_item_note
-- ============================================================
CREATE TABLE IF NOT EXISTS work_item_note (
    id           UUID         NOT NULL,
    work_item_id UUID         NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    content      TEXT         NOT NULL,
    author       VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    edited_at    TIMESTAMP,
    tenancy_id   VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',

    CONSTRAINT pk_work_item_note PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_win_work_item_id       ON work_item_note (work_item_id);
CREATE INDEX IF NOT EXISTS idx_work_item_note_tenancy ON work_item_note (tenancy_id);

-- ============================================================
-- work_item_template
-- ============================================================
CREATE TABLE IF NOT EXISTS work_item_template (
    id                           UUID         NOT NULL,
    name                         VARCHAR(255) NOT NULL,
    description                  TEXT,
    priority                     VARCHAR(20),
    candidate_groups             VARCHAR(500),
    candidate_users              VARCHAR(500),
    required_capabilities        VARCHAR(500),
    default_expiry_hours         INTEGER,
    default_claim_hours          INTEGER,
    default_expiry_business_hours INTEGER,
    default_claim_business_hours INTEGER,
    default_payload              TEXT,
    label_paths                  TEXT,
    type_paths                   TEXT,

    -- Named outcomes
    outcomes                     TEXT,

    -- Data schemas
    input_data_schema            TEXT,
    output_data_schema           TEXT,

    -- Exclusion
    excluded_users               TEXT,
    excluded_groups              TEXT,

    -- Multi-instance
    instance_count               INTEGER,
    required_count               INTEGER,
    parent_role                  VARCHAR(15),
    assignment_strategy          VARCHAR(255),
    on_threshold_reached         VARCHAR(10),
    allow_same_assignee          BOOLEAN,

    -- Scope
    scope                        VARCHAR(255),

    -- Metadata
    created_by                   VARCHAR(255) NOT NULL,
    created_at                   TIMESTAMP    NOT NULL,

    -- OCC / versioning
    version                      BIGINT       NOT NULL DEFAULT 1,

    -- Tenancy
    tenancy_id                   VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',

    CONSTRAINT pk_work_item_template PRIMARY KEY (id),
    CONSTRAINT uq_work_item_template_name_tenant UNIQUE (name, tenancy_id)
);

CREATE INDEX IF NOT EXISTS idx_wit_name                   ON work_item_template (name);
CREATE INDEX IF NOT EXISTS idx_work_item_template_tenancy ON work_item_template (tenancy_id);

-- ============================================================
-- work_item_relation
-- ============================================================
CREATE TABLE IF NOT EXISTS work_item_relation (
    id              UUID         NOT NULL,
    source_id       UUID         NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    target_id       UUID         NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    relation_type   VARCHAR(100) NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    tenancy_id      VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',

    CONSTRAINT pk_work_item_relation PRIMARY KEY (id),
    CONSTRAINT uq_work_item_relation UNIQUE (source_id, target_id, relation_type)
);

CREATE INDEX IF NOT EXISTS idx_wir_source_id              ON work_item_relation (source_id);
CREATE INDEX IF NOT EXISTS idx_wir_target_id              ON work_item_relation (target_id);
CREATE INDEX IF NOT EXISTS idx_wir_relation_type          ON work_item_relation (relation_type);
CREATE INDEX IF NOT EXISTS idx_work_item_relation_tenancy ON work_item_relation (tenancy_id);

-- ============================================================
-- work_item_schedule
-- ============================================================
CREATE TABLE IF NOT EXISTS work_item_schedule (
    id              UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    template_id     UUID         NOT NULL REFERENCES work_item_template(id) ON DELETE CASCADE,
    cron_expression VARCHAR(255) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    last_fired_at   TIMESTAMP,
    next_fire_at    TIMESTAMP,
    version         BIGINT       NOT NULL DEFAULT 0,
    tenancy_id      VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',

    CONSTRAINT pk_work_item_schedule PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_wis_next_fire_at            ON work_item_schedule (next_fire_at);
CREATE INDEX IF NOT EXISTS idx_wis_active                  ON work_item_schedule (active);
CREATE INDEX IF NOT EXISTS idx_work_item_schedule_tenancy  ON work_item_schedule (tenancy_id);

-- ============================================================
-- work_item_link
-- ============================================================
CREATE TABLE IF NOT EXISTS work_item_link (
    id              UUID          NOT NULL,
    work_item_id    UUID          NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    url             VARCHAR(2000) NOT NULL,
    title           VARCHAR(500),
    relation_type   VARCHAR(100)  NOT NULL,
    linked_by       VARCHAR(255)  NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    tenancy_id      VARCHAR(255)  NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',

    CONSTRAINT pk_work_item_link PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_wlink_work_item_id       ON work_item_link (work_item_id);
CREATE INDEX IF NOT EXISTS idx_wlink_relation_type      ON work_item_link (relation_type);
CREATE INDEX IF NOT EXISTS idx_work_item_link_tenancy   ON work_item_link (tenancy_id);

-- ============================================================
-- work_item_spawn_group
-- ============================================================
CREATE TABLE IF NOT EXISTS work_item_spawn_group (
    id                   UUID          NOT NULL,
    parent_id            UUID          NOT NULL,
    idempotency_key      VARCHAR(255)  NOT NULL,
    created_at           TIMESTAMP     NOT NULL,

    -- Multi-instance policy
    instance_count       INTEGER,
    required_count       INTEGER,
    on_threshold_reached VARCHAR(10),
    allow_same_assignee  BOOLEAN       NOT NULL DEFAULT FALSE,
    parent_role          VARCHAR(15),
    completed_count      INTEGER       NOT NULL DEFAULT 0,
    rejected_count       INTEGER       NOT NULL DEFAULT 0,
    policy_triggered     BOOLEAN       NOT NULL DEFAULT FALSE,
    group_status         VARCHAR(15),

    -- OCC
    version              BIGINT        NOT NULL DEFAULT 0,

    -- Tenancy
    tenancy_id           VARCHAR(255)  NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',

    CONSTRAINT pk_work_item_spawn_group PRIMARY KEY (id),
    CONSTRAINT uq_spawn_group_idempotency UNIQUE (parent_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_work_item_spawn_group_tenancy ON work_item_spawn_group (tenancy_id);

-- ============================================================
-- routing_cursor
-- ============================================================
CREATE TABLE IF NOT EXISTS routing_cursor (
    pool_hash     VARCHAR(64)               NOT NULL,
    last_index    INTEGER                   NOT NULL DEFAULT -1,
    version       INTEGER                   NOT NULL DEFAULT 0,
    last_accessed TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now(),
    tenancy_id    VARCHAR(255)              NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',

    PRIMARY KEY (pool_hash, tenancy_id)
);

CREATE INDEX IF NOT EXISTS idx_routing_cursor_tenancy ON routing_cursor (tenancy_id);

-- ============================================================
-- label_rule (replaces filter_rule — V5004)
-- ============================================================
CREATE TABLE IF NOT EXISTS label_rule (
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
