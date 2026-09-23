package com.aifishing.planning.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratePlanRequestFieldsTest {

    @Test
    void generatePlanRequestFieldSetIsUnchanged() {
        assertThat(Arrays.stream(GeneratePlanRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("strategyRunId", "accessPointId", "featurePipeline", "includeAiTactics");
    }
}
