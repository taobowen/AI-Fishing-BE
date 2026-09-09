package com.aifishing.strategy.service;

import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.context.PipelineReadiness;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.weather.WeatherAvailability;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemConfidenceCalculatorTest {

    private final SystemConfidenceCalculator calculator = new SystemConfidenceCalculator();

    @Test
    void weatherSpeciesAndStructureChangeScoreIndependentlyOfModelConfidence() {
        FishingStrategyProfile highModel = withModel(0.99);
        FishingStrategyProfile lowModel = withModel(0.01);

        FishingContext rich = StrategyFixtures.context(
                PipelineReadiness.READY, WeatherAvailability.FORECAST_AVAILABLE, true, 4, 0.85, 18.0);
        FishingContext poorWeather = StrategyFixtures.context(
                PipelineReadiness.READY, WeatherAvailability.FAILED, true, 4, 0.85, 18.0);
        FishingContext unconfirmed = StrategyFixtures.context(
                PipelineReadiness.READY, WeatherAvailability.FORECAST_AVAILABLE, false, 4, 0.85, 18.0);
        FishingContext none = StrategyFixtures.context(
                PipelineReadiness.READY, WeatherAvailability.FORECAST_AVAILABLE, true, 0, null, 18.0);

        double richHigh = calculator.calculate(rich, highModel);
        double richLow = calculator.calculate(rich, lowModel);
        assertThat(richHigh).isEqualTo(richLow);
        assertThat(richHigh).isBetween(SystemConfidenceCalculator.MIN, SystemConfidenceCalculator.MAX);

        assertThat(calculator.calculate(poorWeather, highModel)).isLessThan(richHigh);
        assertThat(calculator.calculate(unconfirmed, highModel)).isLessThan(richHigh);
        assertThat(calculator.calculate(none, highModel)).isLessThan(richHigh);
        assertThat(calculator.calculate(poorWeather, highModel)).isNotEqualTo(calculator.calculate(unconfirmed, highModel));
    }

    @Test
    void manyLimitationCodesCannotCollapseBelowFloor() {
        FishingContext sparse = StrategyFixtures.context(
                PipelineReadiness.PARTIAL, WeatherAvailability.UNAVAILABLE, false, 0, 0.2, 8.0);
        double score = calculator.calculate(sparse, withModel(1.0));
        assertThat(score).isGreaterThanOrEqualTo(SystemConfidenceCalculator.MIN);
        assertThat(score).isLessThanOrEqualTo(SystemConfidenceCalculator.MAX);
    }

    private FishingStrategyProfile withModel(double modelConfidence) {
        FishingStrategyProfile base = StrategyFixtures.validProfile();
        return new FishingStrategyProfile(
                base.targetSpecies(),
                base.structurePreferences(),
                base.timeWindows(),
                base.generalTechniques(),
                base.weatherInterpretation(),
                modelConfidence,
                null,
                base.warnings(),
                base.dataLimitations()
        );
    }
}
