package com.aifishing.planning.tactics;

import com.aifishing.gear.domain.Gear;
import com.aifishing.gear.lure.LureProfile;

import java.util.UUID;

public record LockerLure(
        UUID gearItemId,
        LureProfile profile,
        String name
) {
    public static LockerLure from(Gear gear) {
        if (gear == null) {
            return null;
        }
        LureProfile profile = LureProfile.fromMetadata(gear.getMetadata());
        if (profile == null || profile.lureFamily() == null) {
            return null;
        }
        return new LockerLure(gear.getId(), profile, gear.getName());
    }
}
