package com.aifishing.guidance.contracts;

import java.util.List;

public record SafetyVerdict(
        String schemaVersion,
        SafetyVerdictLevel level,
        List<String> reasons,
        List<SafetyConstraintCode> constraintCodes,
        List<GuidanceAction> allowedActions,
        GuidanceAction prescribedAction
) {
    public SafetyVerdict {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        constraintCodes = constraintCodes == null ? List.of() : List.copyOf(constraintCodes);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        if (level == SafetyVerdictLevel.BLOCK && prescribedAction == null) {
            throw new IllegalArgumentException("prescribedAction is required when level is BLOCK");
        }
        if (level != null && level != SafetyVerdictLevel.BLOCK && prescribedAction != null) {
            throw new IllegalArgumentException("prescribedAction must be null unless level is BLOCK");
        }
    }
}
