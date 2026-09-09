package com.aifishing.gear.api;

import com.aifishing.common.enums.GearType;
import com.aifishing.gear.lure.LureProfile;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateGearRequest(
        @NotNull GearType type,
        String name,
        String brand,
        Map<String, Object> metadata,
        LureProfile lureProfile
) {
}
