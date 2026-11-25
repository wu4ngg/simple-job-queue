ALTER TABLE deliveries
    ADD COLUMN lease_expires_at timestamptz,
    ADD COLUMN claim_token      uuid;

CREATE INDEX deliveries_inflight_lease_expires_at_idx ON deliveries (lease_expires_at) WHERE status = 'inflight';