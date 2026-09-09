package com.aifishing.common.enums;

public enum LureFamily {
    PADDLETAIL,
    MINNOW_SOFT_PLASTIC,
    GRUB,
    TUBE,
    NED_RIG,
    DROP_SHOT_BAIT,
    JIG,
    JERKBAIT,
    CRANKBAIT,
    LIPLESS_CRANKBAIT,
    SPINNERBAIT,
    CHATTERBAIT,
    SPOON,
    INLINE_SPINNER,
    TOPWATER,
    FROG,
    TEXAS_RIG,
    CAROLINA_RIG,
    BUZZBAIT,
    OTHER;

    public boolean requiresLength() {
        return !requiresWeight();
    }

    public boolean requiresWeight() {
        return switch (this) {
            case SPOON, JIG, SPINNERBAIT, CHATTERBAIT, INLINE_SPINNER, BUZZBAIT -> true;
            default -> false;
        };
    }

    public boolean allowsOptionalWeight() {
        return this == PADDLETAIL;
    }

    public String displayName() {
        return switch (this) {
            case PADDLETAIL -> "Paddletail";
            case MINNOW_SOFT_PLASTIC -> "Soft-plastic minnow";
            case GRUB -> "Grub";
            case TUBE -> "Tube";
            case NED_RIG -> "Ned rig";
            case DROP_SHOT_BAIT -> "Drop-shot bait";
            case JIG -> "Jig";
            case JERKBAIT -> "Jerkbait";
            case CRANKBAIT -> "Crankbait";
            case LIPLESS_CRANKBAIT -> "Lipless crankbait";
            case SPINNERBAIT -> "Spinnerbait";
            case CHATTERBAIT -> "Chatterbait";
            case SPOON -> "Spoon";
            case INLINE_SPINNER -> "Inline spinner";
            case TOPWATER -> "Topwater";
            case FROG -> "Frog";
            case TEXAS_RIG -> "Texas rig";
            case CAROLINA_RIG -> "Carolina rig";
            case BUZZBAIT -> "Buzzbait";
            case OTHER -> "Other lure";
        };
    }
}
