package com.aifishing.strategy.ai;

import com.aifishing.lake.processing.OpenAiProperties;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.context.PipelineReadiness;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyPromptFactoryTest {

    @Test
    void promptForbidsSpotsLegalDecisionsAndAirAsWater() {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setStrategyPromptVersion("fishing-strategy-v1");
        StrategyPromptFactory factory = new StrategyPromptFactory(properties, new ObjectMapper().findAndRegisterModules());
        String system = factory.systemPrompt();
        assertThat(system).contains("Do not choose GPS");
        assertThat(system).contains("Do not decide whether fishing is legal");
        assertThat(system).contains("Air temperature is not observed water temperature");
        assertThat(system).contains("NOT_AVAILABLE");
        assertThat(system).contains("day-level priors");
        assertThat(system).contains("lightPreference");
        assertThat(factory.version()).isEqualTo("fishing-strategy-v1");

        String user = factory.userPrompt(
                StrategyFixtures.context(PipelineReadiness.READY, WeatherAvailability.FORECAST_AVAILABLE, true, 2, 0.8, 12.0),
                List.of()
        );
        assertThat(user).contains("WALLEYE");
        assertThat(user).contains("waterTemperatureAvailable");
        assertThat(user).doesNotContain("Sanctuary from the point");
        assertThat(user.toLowerCase()).doesNotContain("featureid");
    }
}
