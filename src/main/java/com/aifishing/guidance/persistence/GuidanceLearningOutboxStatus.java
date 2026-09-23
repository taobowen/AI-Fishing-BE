package com.aifishing.guidance.persistence;

public enum GuidanceLearningOutboxStatus {
    PENDING,
    CLAIMED,
    DONE,
    FAILED,
    DLQ,
    QUARANTINED
}
