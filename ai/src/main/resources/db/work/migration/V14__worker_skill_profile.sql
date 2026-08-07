CREATE TABLE worker_skill_profile (
    worker_id    VARCHAR(255) NOT NULL,
    tenancy_id   VARCHAR(255) NOT NULL,
    narrative    TEXT,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT pk_worker_skill_profile PRIMARY KEY (worker_id)
);

CREATE INDEX idx_worker_skill_profile_tenancy ON worker_skill_profile(tenancy_id);
