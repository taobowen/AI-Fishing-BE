package com.aifishing.common.enums;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LureWeightBand {
    UNDER_1_8("UNDER_1_8", "Under 1/8 oz"),
    FROM_1_8_TO_1_4("1_8_TO_1_4", "1/8–1/4 oz"),
    FROM_1_4_TO_3_8("1_4_TO_3_8", "1/4–3/8 oz"),
    FROM_3_8_TO_1_2("3_8_TO_1_2", "3/8–1/2 oz"),
    FROM_1_2_TO_3_4("1_2_TO_3_4", "1/2–3/4 oz"),
    OVER_3_4("OVER_3_4", "Over 3/4 oz");

    private final String wire;
    private final String displayName;

    LureWeightBand(String wire, String displayName) {
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
    @JsonAlias({
            "1_8_TO_1_4",
            "1_4_TO_3_8",
            "3_8_TO_1_2",
            "1_2_TO_3_4"
    })
    public static LureWeightBand from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (LureWeightBand band : values()) {
            if (band.wire.equalsIgnoreCase(value) || band.name().equalsIgnoreCase(value)) {
                return band;
            }
        }
        throw new IllegalArgumentException("Unknown lure weight band: " + value);
    }
}
