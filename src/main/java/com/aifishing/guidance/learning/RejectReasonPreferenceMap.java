package com.aifishing.guidance.learning;

import com.aifishing.guidance.contracts.GuidanceRejectReason;

import java.util.List;
import java.util.Map;

public final class RejectReasonPreferenceMap {

    public record MappedPreference(String key, String value) {
    }

    private RejectReasonPreferenceMap() {
    }

    public static List<MappedPreference> mappings(GuidanceRejectReason reason, Map<String, Object> payload) {
        if (reason == null) {
            return List.of();
        }
        return switch (reason) {
            case TOO_FAR -> List.of(new MappedPreference(
                    InferredPreferenceKeys.MAX_MOVE_METERS,
                    firstText(payload, "maxMoveMeters", "constrained")
            ));
            case TOO_ROUGH -> List.of(
                    new MappedPreference(InferredPreferenceKeys.AVOID_LONG_MOVE_IN_WIND, "true"),
                    new MappedPreference(
                            InferredPreferenceKeys.WIND_CONSERVATISM,
                            firstText(payload, "windConservatism", "elevated")
                    )
            );
            case WANT_TO_STAY -> List.of(new MappedPreference(InferredPreferenceKeys.STAY_PREFERRED, "true"));
            case DO_NOT_WANT_LURE_CHANGE -> List.of(
                    new MappedPreference(InferredPreferenceKeys.AVOID_LURE_CHANGE, "true")
            );
            case OTHER, UNSPECIFIED -> List.of();
        };
    }

    public static boolean writesSemanticNote(GuidanceRejectReason reason) {
        return reason == null || reason == GuidanceRejectReason.OTHER || reason == GuidanceRejectReason.UNSPECIFIED;
    }

    private static String firstText(Map<String, Object> payload, String key, String fallback) {
        if (payload == null || payload.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(payload.get(key)).trim();
        return value.isBlank() ? fallback : value;
    }
}
