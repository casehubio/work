-- quarkus-work-queues V2000: filters, filter chains, queue state, queue snapshots
-- Compatible with H2 (dev/test) and PostgreSQL (production).
--
-- Consolidated from: V2000, V2002, V2003, V2004, V5003.
-- queue_view and work_item_queue_membership migrated to platform subject_view
-- tables (platform V5000+V5001) — no longer owned by this module.

-- ── work_item_filter ────────────────────────────────────────────────────

CREATE TABLE work_item_filter (
    id                   UUID            PRIMARY KEY,
    tenancy_id           VARCHAR(255)    NOT NULL,
    name                 VARCHAR(255)    NOT NULL,
    scope                VARCHAR(500)    NOT NULL,
    condition_language   VARCHAR(20)     NOT NULL,
    condition_expression VARCHAR(4000),
    actions              VARCHAR(4000),
    active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP       NOT NULL
);

CREATE INDEX idx_work_item_filter_tenancy ON work_item_filter(tenancy_id);

-- ── filter_chain ────────────────────────────────────────────────────────

CREATE TABLE filter_chain (
    id          UUID            PRIMARY KEY,
    tenancy_id  VARCHAR(255)    NOT NULL,
    filter_id   UUID            NOT NULL REFERENCES work_item_filter(id) ON DELETE CASCADE
);

CREATE INDEX idx_fc_filter_id ON filter_chain(filter_id);
CREATE INDEX idx_filter_chain_tenancy ON filter_chain(tenancy_id);

-- ── filter_chain_work_item (junction) ───────────────────────────────────

CREATE TABLE filter_chain_work_item (
    filter_chain_id UUID    NOT NULL REFERENCES filter_chain(id) ON DELETE CASCADE,
    work_item_id    UUID    NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    PRIMARY KEY (filter_chain_id, work_item_id)
);

CREATE INDEX idx_fcwi_work_item_id ON filter_chain_work_item(work_item_id);

-- ── work_item_queue_state ───────────────────────────────────────────────

CREATE TABLE work_item_queue_state (
    work_item_id    UUID        PRIMARY KEY REFERENCES work_item(id) ON DELETE CASCADE,
    tenancy_id      VARCHAR(255) NOT NULL,
    relinquishable  BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_work_item_queue_state_tenancy ON work_item_queue_state(tenancy_id);

-- ── queue_snapshot ──────────────────────────────────────────────────────
-- NOTE: queue_view_id references subject_view(id) in the platform module.
-- Platform migrations V5000+V5001 must run before this migration.

CREATE TABLE queue_snapshot (
    id            UUID         NOT NULL,
    tenancy_id    VARCHAR(255) NOT NULL,
    queue_view_id UUID         NOT NULL,
    member_count  BIGINT       NOT NULL,
    snapshot_at   TIMESTAMP    NOT NULL,
    CONSTRAINT pk_queue_snapshot PRIMARY KEY (id),
    -- No FK to subject_view(id) — cross-module reference; platform creates that table
    CONSTRAINT uq_queue_snapshot_tenant_queue_time
        UNIQUE (tenancy_id, queue_view_id, snapshot_at)
);

CREATE INDEX idx_queue_snapshot_trend
    ON queue_snapshot (tenancy_id, queue_view_id, snapshot_at);
