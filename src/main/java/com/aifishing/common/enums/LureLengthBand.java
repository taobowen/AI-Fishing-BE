package com.aifishing.common.enums;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LureLengthBand {
    UNDER_3_IN("UNDER_3_IN", "Under 3 in"),
    THREE_TO_4_IN("3_TO_4_IN", "3–4 in"),
    FOUR_TO_5_IN("4_TO_5_IN", "4–5 in"),
    OVER_5_IN("OVER_5_IN", "Over 5 in");

    private final String wire;
    private final String displayName;

    LureLengthBand(String wire, String displayName) {
        this.wire = wire;
        this.displayName = displayName;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public String displayName() {
        return displayName;
    }

    @JsonCreator
    @JsonAlias({"THREE_TO_4_IN", "FOUR_TO_5_IN"})
    public static LureLengthBand from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (LureLengthBand band : values()) {
            if (band.wire.equalsIgnoreCase(value) || band.name().equalsIgnoreCase(value)) {
                return band;
            }
        }
        throw new IllegalArgumentException("Unknown lure length band: " + value);
    }
}
