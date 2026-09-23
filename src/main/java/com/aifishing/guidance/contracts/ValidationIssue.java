package com.aifishing.guidance.contracts;

public record ValidationIssue(
        DecisionValidationCheck check,
        String message
) {
}
