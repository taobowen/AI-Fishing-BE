package com.aifishing.guidance.contracts;

/**
 * Non-retryable learning-outbox quarantine reason.
 * {@link #UNKNOWN_JOB_TYPE} must not remain {@code PENDING}.
 */
public enum LearningQuarantineReason {
    UNKNOWN_JOB_TYPE
}
