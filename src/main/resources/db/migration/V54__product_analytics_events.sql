-- Product analytics events for planning funnel instrumentation (client-fired).

CREATE TABLE product_analytics_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    name VARCHAR(128) NOT NULL,
    properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_analytics_events_user_created
    ON product_analytics_events (user_id, created_at DESC);

CREATE INDEX idx_product_analytics_events_name_created
    ON product_analytics_events (name, created_at DESC);
