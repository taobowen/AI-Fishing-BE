-- Additive denominators so ONLINE_METRICS_ROLLUP can SUM raw counts then derive rates.
-- Missing rates stay uncomputed (NULL in OnlineGuidanceMetrics), never stored as 0.

ALTER TABLE guidance_online_metric_rollups
    ADD COLUMN follow_through_eligible_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN override_eligible_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN feedback_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE guidance_online_metric_rollups
    ADD CONSTRAINT guidance_online_metric_rollups_denominators_check CHECK (
        follow_through_eligible_count >= 0
        AND override_eligible_count >= 0
        AND feedback_count >= 0
    );
