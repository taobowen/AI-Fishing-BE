package com.aifishing.common.enums;

public enum FishingSessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED;

    public boolean isUnfinished() {
        return this == ACTIVE || this == PAUSED;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
