-- Federation subscription model
CREATE TABLE federation_subscription (
    id UUID PRIMARY KEY,
    peer_id VARCHAR(255) NOT NULL,
    callback_url VARCHAR(1024) NOT NULL,
    base_url VARCHAR(1024) NOT NULL,
    tenancy_id VARCHAR(255) NOT NULL,
    filter_json TEXT NOT NULL,
    capabilities_json TEXT,
    hmac_secret_encrypted BYTEA NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    consecutive_failures INT NOT NULL DEFAULT 0,
    last_failure_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE federation_subscription_tracking (
    subscription_id UUID NOT NULL REFERENCES federation_subscription(id),
    work_item_id UUID NOT NULL,
    PRIMARY KEY (subscription_id, work_item_id)
);

CREATE INDEX idx_fed_tracking_work_item ON federation_subscription_tracking (work_item_id);
