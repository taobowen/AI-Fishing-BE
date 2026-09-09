package com.aifishing.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PropulsionType {
    NONE,
    PADDLE,
    PEDAL,
    ELECTRIC_TROLLING,
    ELECTRIC_OUTBOARD,
    GAS_OUTBOARD;

    @JsonCreator
    public static PropulsionType fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim()) {
            case "OUTBOARD" -> GAS_OUTBOARD;
            case "TROLLING_MOTOR" -> ELECTRIC_TROLLING;
            default -> valueOf(raw.trim());
        };
    }
}
