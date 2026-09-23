package com.aifishing.guidance.eval;

import com.aifishing.guidance.GuidanceProperties;

/**
 * Explicit price book. Cost stays {@code null} unless {@code pricingVersion} and
 * both per-1k rates are present and token counts are known.
 */
public record TokenPricing(
        String pricingVersion,
        Double inputUsdPer1kTokens,
        Double outputUsdPer1kTokens
) {
    public TokenPricing {
        if (pricingVersion != null && pricingVersion.isBlank()) {
            pricingVersion = null;
        }
    }

    public static TokenPricing none() {
        return new TokenPricing(null, null, null);
    }

    public static TokenPricing from(GuidanceProperties properties) {
        if (properties == null || properties.getEval() == null) {
            return none();
        }
        GuidanceProperties.Eval eval = properties.getEval();
        return new TokenPricing(
                eval.getPricingVersion(),
                eval.getInputUsdPer1kTokens(),
                eval.getOutputUsdPer1kTokens()
        );
    }

    public boolean canPrice() {
        return pricingVersion != null
                && inputUsdPer1kTokens != null
                && outputUsdPer1kTokens != null;
    }

    public Double costUsd(Integer inputTokens, Integer outputTokens) {
        if (!canPrice() || inputTokens == null || outputTokens == null) {
            return null;
        }
        return (inputTokens / 1000.0) * inputUsdPer1kTokens
                + (outputTokens / 1000.0) * outputUsdPer1kTokens;
    }
}
