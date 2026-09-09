ALTER TABLE users
    ADD COLUMN auth_provider VARCHAR(64),
    ADD COLUMN auth_subject VARCHAR(255);

CREATE UNIQUE INDEX users_auth_identity_key
    ON users (auth_provider, auth_subject)
    WHERE auth_provider IS NOT NULL AND auth_subject IS NOT NULL;

CREATE TABLE catch_photos (
    id UUID PRIMARY KEY,
    catch_event_id UUID NOT NULL REFERENCES catch_events (id),
    s3_key VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    content_type VARCHAR(128),
    size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_catch_photos_catch
    ON catch_photos (catch_event_id, created_at);
