package com.aifishing.guidance.contracts;

import java.util.List;

public record DecisionValidationResult(
        String schemaVersion,
        boolean valid,
        List<ValidationIssue> issues
) {
}
