package com.aifishing.gear.lure;

import com.aifishing.common.enums.GearType;
import com.aifishing.common.exception.BadRequestException;

public final class LureProfileValidator {

    private LureProfileValidator() {
    }

    public static void validateIfPresent(GearType type, LureProfile profile) {
        if (type != GearType.LURE || profile == null) {
            return;
        }
        if (profile.lureFamily() == null) {
            throw new BadRequestException("LURE_PROFILE_INVALID", "Lure family is required");
        }
        if (profile.requiresLength() && profile.lengthBand() == null) {
            throw new BadRequestException("LURE_PROFILE_INVALID", "Length is required for this lure family");
        }
        if (profile.requiresWeight() && profile.weightBand() == null) {
            throw new BadRequestException("LURE_PROFILE_INVALID", "Weight is required for this lure family");
        }
        if (profile.colors() == null || profile.colors().isEmpty()) {
            throw new BadRequestException("LURE_PROFILE_INVALID", "At least one color is required");
        }
    }
}
