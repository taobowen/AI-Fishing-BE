-- Raw provider usage + derived UsageTelemetry. Tokens/cost stay NULL when unknown.

ALTER TABLE agent_runs
    ADD COLUMN raw_provider_usage JSONB,
    ADD COLUMN usage_telemetry JSONB;

ALTER TABLE guidance_eval_case_results
    ADD COLUMN raw_provider_usage JSONB,
    ADD COLUMN usage_telemetry JSONB;
