package com.aifishing.planning.service;

import java.time.LocalTime;
import java.util.Locale;

final class SpotReason {

    private SpotReason() {
    }

    static String format(
            String typeName,
            Double depthM,
            Double confidence,
            LocalTime from,
            LocalTime to,
            String rationale
    ) {
        StringBuilder text = new StringBuilder(typeLabel(typeName));
        if (depthM != null) {
            text.append(" at ").append(String.format(Locale.ROOT, "%.1fm", depthM));
        }
        if (confidence != null) {
            text.append(" (").append(Math.round(confidence * 100)).append("% feature confidence)");
        }
        if (from != null && to != null) {
            text.append(" during ").append(from).append("–").append(to);
        }
        text.append(".");
        if (rationale != null && !rationale.isBlank()) {
            text.append(" ").append(rationale.trim());
        }
        return text.toString();
    }

    private static String typeLabel(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return "structure";
        }
        String normalized = typeName.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        if ("drop off".equals(normalized)) {
            return "drop-off";
        }
        return normalized;
    }
}
