CREATE TABLE queue_snapshot (
    id            UUID         NOT NULL,
    tenancy_id    VARCHAR(255) NOT NULL,
    queue_view_id UUID         NOT NULL,
    member_count  BIGINT       NOT NULL,
    snapshot_at   TIMESTAMP    NOT NULL,
    CONSTRAINT pk_queue_snapshot PRIMARY KEY (id),
    CONSTRAINT fk_queue_snapshot_queue_view
        FOREIGN KEY (queue_view_id) REFERENCES queue_view(id) ON DELETE CASCADE,
    CONSTRAINT uq_queue_snapshot_tenant_queue_time
        UNIQUE (tenancy_id, queue_view_id, snapshot_at)
);

CREATE INDEX idx_queue_snapshot_trend
    ON queue_snapshot (tenancy_id, queue_view_id, snapshot_at);
