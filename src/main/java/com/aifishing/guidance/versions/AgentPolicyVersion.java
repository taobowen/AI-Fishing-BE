package com.aifishing.guidance.versions;

import java.util.Objects;

/**
 * Catalog entry for an available Agent policy version. Profile ids are resolved
 * by {@link AgentPolicyResolver}; this record does not select live production.
 */
public record AgentPolicyVersion(
        String version,
        String promptProfile,
        String contextProfile,
        String toolsetProfile,
        String modelProfile,
        String learningProfile
) {
    public static final String V1 = "v1";
    public static final String V2 = "v2";

    public AgentPolicyVersion {
        version = requireId("version", version);
        promptProfile = requireId("promptProfile", promptProfile);
        contextProfile = requireId("contextProfile", contextProfile);
        toolsetProfile = requireId("toolsetProfile", toolsetProfile);
        modelProfile = requireId("modelProfile", modelProfile);
        learningProfile = requireId("learningProfile", learningProfile);
    }

    private static String requireId(String field, String value) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return trimmed;
    }
}
