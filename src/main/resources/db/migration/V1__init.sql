CREATE TABLE endpoints (
    id UUID PRIMARY KEY,
    url TEXT NOT NULL,
    secret TEXT NOT NULL
);
CREATE TABLE deliveries (
    id UUID PRIMARY KEY NOT NULL,
    endpoint_id UUID NOT NULL REFERENCES endpoints (id) ON DELETE RESTRICT,
    payload TEXT NOT NULL,
    status TEXT NOT NULL CONSTRAINT deliveries_status_check CHECK (status in ('dead', 'inflight', 'pending', 'succeeded')),
    attempts INTEGER NOT NULL CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX deliveries_pending_next_attempt_at_idx ON deliveries (next_attempt_at) WHERE status = 'pending';
CREATE INDEX deliveries_endpoint_id_idx ON deliveries (endpoint_id);
