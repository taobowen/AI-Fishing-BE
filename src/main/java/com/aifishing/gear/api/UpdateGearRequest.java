package com.aifishing.gear.api;

import com.aifishing.common.enums.GearType;
import com.aifishing.gear.lure.LureProfile;

import java.util.Map;

public record UpdateGearRequest(
        GearType type,
        String name,
        String brand,
        Map<String, Object> metadata,
        Boolean active,
        LureProfile lureProfile
) {
}
