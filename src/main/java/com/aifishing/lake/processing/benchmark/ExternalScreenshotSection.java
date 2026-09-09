package com.aifishing.lake.processing.benchmark;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExternalScreenshotSection(
        Object model,
        Object promptVersion,
        Object screenshotDescription,
        Object source,
        Object featureCountsByType,
        Object precision,
        Object recall,
        Object f1,
        Object perType,
        Object notes
) {
    @SuppressWarnings("unchecked")
    public static ExternalScreenshotSection from(Map<String, Object> body) {
        return new ExternalScreenshotSection(
                body.get("model"),
                body.get("promptVersion"),
                body.get("screenshotDescription"),
                body.get("source"),
                body.getOrDefault("featureCountsByType", Map.of()),
                body.get("precision"),
                body.get("recall"),
                body.get("f1"),
                body.get("perType"),
                body.get("notes")
        );
    }
}
