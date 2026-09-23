package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceEnumAlignmentTest {

    @ParameterizedTest
    @MethodSource("enumPairs")
    void javaEnumsMatchSchema(String defName, List<String> javaValues) {
        JsonNode schemaEnum = GuidanceContracts.def(defName).get("enum");
        assertThat(schemaEnum).isNotNull();
        List<String> schemaValues = schemaEnum.valueStream().map(JsonNode::asText).toList();
        assertThat(javaValues).containsExactlyElementsOf(schemaValues);
    }

    @Test
    void schemaVersionConstMatchesJava() {
        assertThat(GuidanceContracts.def("SchemaVersion").path("const").asText())
                .isEqualTo(GuidanceSchemaVersion.VALUE);
    }

    static Stream<Arguments> enumPairs() {
        return Stream.of(
                Arguments.of("SessionEventType", names(SessionEventType.class)),
                Arguments.of("GuidanceTrigger", names(GuidanceTrigger.class)),
                Arguments.of("FishingActivityState", names(FishingActivityState.class)),
                Arguments.of("ActivityStateSource", names(ActivityStateSource.class)),
                Arguments.of("GuidanceAction", names(GuidanceAction.class)),
                Arguments.of("LureFamily", names(LureFamily.class)),
                Arguments.of("PresentationTechnique", names(PresentationTechnique.class)),
                Arguments.of("RetrieveStyle", names(RetrieveStyle.class)),
                Arguments.of("ToolName", Arrays.stream(ToolName.values()).map(ToolName::wire).toList()),
                Arguments.of("ToolResultStatus", names(ToolResultStatus.class)),
                Arguments.of("FeedbackStatus", names(FeedbackStatus.class)),
                Arguments.of("ReplanScope", names(ReplanScope.class)),
                Arguments.of("EventSource", names(EventSource.class)),
                Arguments.of("AgentRunStatus", names(AgentRunStatus.class)),
                Arguments.of("DecisionValidationCheck", names(DecisionValidationCheck.class)),
                Arguments.of("PlanCreatedBy", names(PlanCreatedBy.class)),
                Arguments.of("CompassDirection", names(CompassDirection.class)),
                Arguments.of("WeatherCondition", names(WeatherCondition.class)),
                Arguments.of("SafetyVerdictLevel", names(SafetyVerdictLevel.class)),
                Arguments.of("SafetyConstraintCode", names(SafetyConstraintCode.class)),
                Arguments.of("ReflectionClaimKind", names(ReflectionClaimKind.class)),
                Arguments.of("ReflectionCauseKind", names(ReflectionCauseKind.class)),
                Arguments.of("GuidanceRejectReason", names(GuidanceRejectReason.class)),
                Arguments.of("SeasonBucket", names(SeasonBucket.class)),
                Arguments.of("TimeBucket", names(TimeBucket.class)),
                Arguments.of("WindBucket", names(WindBucket.class)),
                Arguments.of("WindDirectionBucket", names(WindDirectionBucket.class)),
                Arguments.of("SemanticMemoryKind", names(SemanticMemoryKind.class)),
                Arguments.of("LiveWaypointPressure", names(LiveWaypointPressure.class)),
                Arguments.of("OutcomeKind", names(OutcomeKind.class)),
                Arguments.of("AttributionDimension", names(AttributionDimension.class)),
                Arguments.of("RecommendationRole", names(RecommendationRole.class)),
                Arguments.of("AttributionWindowKind", names(AttributionWindowKind.class)),
                Arguments.of("LearningJobType", names(LearningJobType.class)),
                Arguments.of("EvalSuiteKind", names(EvalSuiteKind.class)),
                Arguments.of("ReplayMode", names(ReplayMode.class)),
                Arguments.of("EvalExecutionMode", names(EvalExecutionMode.class)),
                Arguments.of("EvalCaseResultStatus", names(EvalCaseResultStatus.class)),
                Arguments.of("GuidanceSuccessKind", names(GuidanceSuccessKind.class)),
                Arguments.of("RecomputedComponent", names(RecomputedComponent.class)),
                Arguments.of("LearningQuarantineReason", names(LearningQuarantineReason.class)),
                Arguments.of("FishSpecies", names(FishSpecies.class)),
                Arguments.of("FishingSessionStatus", names(FishingSessionStatus.class)),
                Arguments.of("BoatType", names(BoatType.class)),
                Arguments.of("PropulsionType", names(PropulsionType.class)),
                Arguments.of("FeatureType", names(FeatureType.class)),
                Arguments.of("TechniqueType", names(TechniqueType.class))
        );
    }

    private static List<String> names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }
}
