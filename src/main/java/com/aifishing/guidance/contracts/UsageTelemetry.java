package com.aifishing.guidance.contracts;

/**
 * Unknown token or cost usage stays {@code null}, never numeric {@code 0}.
 * Amounts require an explicit {@code pricingVersion}.
 */
public record UsageTelemetry(
        String schemaVersion,
        String modelProvider,
        String modelName,
        String modelVersion,
        String promptVersion,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Double costUsd,
        String pricingVersion
) {
    public UsageTelemetry {
        if (pricingVersion != null && pricingVersion.isBlank()) {
            pricingVersion = null;
        }
        if (pricingVersion == null) {
            costUsd = null;
        }
    }

    public boolean hasTokenTelemetry() {
        return inputTokens != null || outputTokens != null || totalTokens != null;
    }
}
