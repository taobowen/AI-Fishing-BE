package com.aifishing.guidance.versions;

import com.aifishing.guidance.runtime.ModelTurnClient;

/**
 * Model adapter used for a bound run. {@code current} is today's production client.
 */
public record ModelProfile(
        String version,
        String provider,
        String modelName,
        String modelVersion,
        ModelTurnClient client
) {
    public static final String CURRENT = "current";

    public static ModelProfile current(ModelTurnClient client) {
        return new ModelProfile(
                CURRENT,
                client.provider(),
                client.modelName(),
                client.modelVersion(),
                client
        );
    }
}
