package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.UsageTelemetry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builds {@link UsageTelemetry} from raw provider usage. Missing token or cost
 * fields stay {@code null}; this never invents numeric {@code 0}. Monetary cost
 * is derived only when {@code pricingVersion} is explicit and rates exist.
 */
public final class UsageTelemetryAssembler {

    private UsageTelemetryAssembler() {
    }

    public static UsageTelemetry assemble(
            String modelProvider,
            String modelName,
            String modelVersion,
            String promptVersion,
            Map<String, Object> rawProviderUsage,
            TokenPricing pricing
    ) {
        Integer input = token(rawProviderUsage, "inputTokens", "input_tokens", "promptTokens", "prompt_tokens");
        Integer output = token(rawProviderUsage, "outputTokens", "output_tokens", "completionTokens", "completion_tokens");
        Integer total = token(rawProviderUsage, "totalTokens", "total_tokens");
        if (total == null && input != null && output != null) {
            total = input + output;
        }
        String pricingVersion = pricing == null ? null : pricing.pricingVersion();
        Double costUsd = pricing == null ? null : pricing.costUsd(input, output);
        return new UsageTelemetry(
                GuidanceSchemaVersion.VALUE,
                blankToNull(modelProvider),
                blankToNull(modelName),
                blankToNull(modelVersion),
                blankToNull(promptVersion),
                input,
                output,
                total,
                costUsd,
                pricingVersion
        );
    }

    public static Map<String, Object> copyRaw(Map<String, Object> rawProviderUsage) {
        if (rawProviderUsage == null || rawProviderUsage.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(rawProviderUsage));
    }

    private static Integer token(Map<String, Object> raw, String... keys) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) {
                value = raw.get(key.toLowerCase(Locale.ROOT));
            }
            if (value == null) {
                continue;
            }
            Integer parsed = asInteger(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
