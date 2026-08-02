CREATE TABLE progress_instance (
    id                  UUID         NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    tenancy_id          VARCHAR(255) NOT NULL,
    scope_type          VARCHAR(255) NOT NULL,
    scope_id            VARCHAR(255) NOT NULL,
    parent_progress_id  UUID,
    root_progress_id    UUID         NOT NULL,
    shape_type          VARCHAR(50)  NOT NULL,
    definition          JSONB,
    state               JSONB        NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    rollup_strategy_id  VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT pk_progress_instance PRIMARY KEY (id)
);

CREATE INDEX idx_progress_scope ON progress_instance (scope_type, scope_id);
CREATE INDEX idx_progress_parent ON progress_instance (parent_progress_id);
CREATE INDEX idx_progress_root ON progress_instance (root_progress_id);
CREATE INDEX idx_progress_tenancy ON progress_instance (tenancy_id);

CREATE TABLE progress_event (
    id                UUID         NOT NULL,
    tenancy_id        VARCHAR(255) NOT NULL,
    progress_id       UUID         NOT NULL,
    root_progress_id  UUID         NOT NULL,
    scope_type        VARCHAR(255) NOT NULL,
    scope_id          VARCHAR(255) NOT NULL,
    change_type       VARCHAR(30)  NOT NULL,
    previous_state    JSONB,
    current_state     JSONB,
    status            VARCHAR(20)  NOT NULL,
    occurred_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_progress_event PRIMARY KEY (id)
);

CREATE INDEX idx_progress_event_progress ON progress_event (progress_id, occurred_at);
CREATE INDEX idx_progress_event_root ON progress_event (root_progress_id, occurred_at);
CREATE INDEX idx_progress_event_tenancy ON progress_event (tenancy_id);
CREATE INDEX idx_progress_event_scope ON progress_event (scope_type, scope_id, occurred_at);
