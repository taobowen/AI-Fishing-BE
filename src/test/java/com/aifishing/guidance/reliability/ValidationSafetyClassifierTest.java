package com.aifishing.guidance.reliability;

import com.aifishing.guidance.contracts.DecisionValidationCheck;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationSafetyClassifierTest {

    @Test
    void classifiesUnsafeAndInvalidWaypointChecks() {
        assertThat(ValidationSafetyClassifier.isUnsafe(DecisionValidationCheck.WEATHER_UNSAFE)).isTrue();
        assertThat(ValidationSafetyClassifier.isUnsafe(DecisionValidationCheck.NO_FISHING_ZONE)).isTrue();
        assertThat(ValidationSafetyClassifier.isUnsafe(DecisionValidationCheck.ACTION_INCOMPATIBLE)).isTrue();
        assertThat(ValidationSafetyClassifier.isUnsafe(DecisionValidationCheck.WAYPOINT_NOT_FOUND)).isFalse();
        assertThat(ValidationSafetyClassifier.isUnsafe(DecisionValidationCheck.SCHEMA_INVALID)).isFalse();

        assertThat(ValidationSafetyClassifier.isInvalidWaypoint(DecisionValidationCheck.WAYPOINT_NOT_FOUND)).isTrue();
        assertThat(ValidationSafetyClassifier.isInvalidWaypoint(DecisionValidationCheck.WAYPOINT_NOT_ON_LAKE)).isTrue();
        assertThat(ValidationSafetyClassifier.isInvalidWaypoint(DecisionValidationCheck.WEATHER_UNSAFE)).isFalse();
        assertThat(ValidationSafetyClassifier.isInvalidWaypoint(DecisionValidationCheck.SCHEMA_INVALID)).isFalse();
        assertThat(ValidationSafetyClassifier.isUnsafe(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN)).isFalse();
        assertThat(ValidationSafetyClassifier.isUnsafe(DecisionValidationCheck.MOVE_OSCILLATION)).isFalse();
        assertThat(ValidationSafetyClassifier.isInvalidWaypoint(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN))
                .isFalse();
        assertThat(ValidationSafetyClassifier.isInvalidWaypoint(DecisionValidationCheck.MOVE_OSCILLATION)).isFalse();
    }
}
