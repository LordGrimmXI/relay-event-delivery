CREATE TABLE subscriptions (
    destination_id BIGINT NOT NULL REFERENCES destinations(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    PRIMARY KEY (destination_id, event_type)
);

CREATE INDEX idx_subscriptions_event_type ON subscriptions (event_type);

CREATE TABLE deliveries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    destination_id BIGINT NOT NULL REFERENCES destinations(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'DELIVERED', 'PERMANENTLY_FAILED')),
    attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lease_expires_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_event_destination UNIQUE (event_id, destination_id)
);

CREATE INDEX idx_deliveries_due ON deliveries (status, next_attempt_at);

CREATE TABLE delivery_attempts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    delivery_id BIGINT NOT NULL REFERENCES deliveries(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL CHECK (attempt_number >= 1),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    http_status INT,
    error_message TEXT,
    duration_ms INT NOT NULL CHECK (duration_ms >= 0),
    started_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_delivery_attempt UNIQUE (delivery_id, attempt_number)
);