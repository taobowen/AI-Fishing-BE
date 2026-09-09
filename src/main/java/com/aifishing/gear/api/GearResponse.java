package com.aifishing.gear.api;

import com.aifishing.common.enums.GearType;
import com.aifishing.gear.lure.LureProfile;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record GearResponse(
        UUID id,
        UUID userId,
        GearType type,
        String name,
        String brand,
        Map<String, Object> metadata,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        LureProfile lureProfile
) {
}
