package com.aifishing.guidance.reliability;

import com.aifishing.guidance.contracts.DecisionValidationCheck;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.ValidationIssue;

import java.util.EnumSet;
import java.util.Set;

/**
 * Splits validator issues into candidate-unsafe vs invalid-waypoint vs other interceptions.
 */
public final class ValidationSafetyClassifier {

    private static final Set<DecisionValidationCheck> UNSAFE = EnumSet.of(
            DecisionValidationCheck.WEATHER_UNSAFE,
            DecisionValidationCheck.NO_FISHING_ZONE,
            DecisionValidationCheck.GPS_ACCURACY_UNACCEPTABLE,
            DecisionValidationCheck.INSUFFICIENT_REMAINING_RANGE,
            DecisionValidationCheck.INSUFFICIENT_RETURN_RESERVE,
            DecisionValidationCheck.ACTION_INCOMPATIBLE
    );

    private static final Set<DecisionValidationCheck> INVALID_WAYPOINT = EnumSet.of(
            DecisionValidationCheck.WAYPOINT_NOT_FOUND,
            DecisionValidationCheck.WAYPOINT_NOT_NAVIGABLE,
            DecisionValidationCheck.WAYPOINT_ID_INCONSISTENT,
            DecisionValidationCheck.WAYPOINT_NOT_IN_SESSION,
            DecisionValidationCheck.WAYPOINT_NOT_ON_LAKE
    );

    private ValidationSafetyClassifier() {
    }

    public static boolean isUnsafe(DecisionValidationCheck check) {
        return check != null && UNSAFE.contains(check);
    }

    public static boolean isInvalidWaypoint(DecisionValidationCheck check) {
        return check != null && INVALID_WAYPOINT.contains(check);
    }

    public static boolean hasUnsafe(DecisionValidationResult validation) {
        return anyMatch(validation, ValidationSafetyClassifier::isUnsafe);
    }

    public static boolean hasInvalidWaypoint(DecisionValidationResult validation) {
        return anyMatch(validation, ValidationSafetyClassifier::isInvalidWaypoint);
    }

    private static boolean anyMatch(
            DecisionValidationResult validation,
            java.util.function.Predicate<DecisionValidationCheck> predicate
    ) {
        if (validation == null || validation.issues() == null) {
            return false;
        }
        for (ValidationIssue issue : validation.issues()) {
            if (issue != null && predicate.test(issue.check())) {
                return true;
            }
        }
        return false;
    }
}
