package com.aifishing.guidance.validate;

/**
 * Internal applicability outcome. Mapped onto frozen {@code DecisionValidationResult}:
 * FAIL → issue + {@code valid=false}; applicable UNKNOWN → issue
 * ({@code REQUIRED_DATA_MISSING} or the specific check); PASS / NOT_APPLICABLE omitted.
 */
enum CheckStatus {
    PASS,
    FAIL,
    NOT_APPLICABLE,
    UNKNOWN
}
