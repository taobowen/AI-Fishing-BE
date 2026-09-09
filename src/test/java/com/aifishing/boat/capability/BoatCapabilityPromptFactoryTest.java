package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.WindWaveCapability;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoatCapabilityPromptFactoryTest {

    @Test
    void includesFreeTextAndRevisesPriors() {
        BoatCapabilityPromptFactory factory = new BoatCapabilityPromptFactory(
                new BoatCapabilityProperties(),
                new ObjectMapper()
        );
        Boat boat = new Boat();
        boat.setType(BoatType.OTHER);
        boat.setConfigurationDescription("24V 100Ah LiFePO4 added to the 12ft jon");
        String prompt = factory.userPrompt(
                boat,
                List.of(),
                new BoatCapabilityPriors(12.0, 10.0, WindWaveCapability.LOW)
        );
        assertThat(prompt).contains("Revise planning capability");
        assertThat(prompt).contains("24V 100Ah LiFePO4");
        assertThat(prompt).contains("priorMetrics");
        assertThat(prompt).contains("12.0");
        assertThat(factory.systemPrompt()).contains("Do not ignore priors");
    }
}
