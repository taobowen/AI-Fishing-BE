package com.aifishing.planning.environment;

import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.LightPreference;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;

@Component
public class TimeAdjustedSpotUtility {

    private final SolarPositionService solarPositionService;
    private final BoatWeatherPenalty boatWeatherPenalty;

    public TimeAdjustedSpotUtility(SolarPositionService solarPositionService, BoatWeatherPenalty boatWeatherPenalty) {
        this.solarPositionService = solarPositionService;
        this.boatWeatherPenalty = boatWeatherPenalty;
    }

    public Evaluation evaluate(
            RankedCandidate candidate,
            Instant instant,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation,
            double waitPenaltyValue
    ) {
        return evaluateAt(candidate, instant, candidate.spot().getLocation(), context, weather, orientation, waitPenaltyValue);
    }

    public Evaluation evaluateAt(
            RankedCandidate candidate,
            Instant instant,
            org.locationtech.jts.geom.Point sampleLocation,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation,
            double waitPenaltyValue
    ) {
        org.locationtech.jts.geom.Point location = sampleLocation == null ? candidate.spot().getLocation() : sampleLocation;
        PlanningProperties.Environment env = context.properties().getEnvironment();
        WeatherSample sample = weather.at(instant);
        SolarPosition sun = solarPositionService.at(instant, TripClock.zoneId(context), location);
        SolarInfluence influence = SolarInfluence.compute(sun, sample, env.getSolar());
        LightPreference preference = candidate.spot().getLightPreference();
        double orientationExposure = SolarExposure.orientationExposure(sun, orientation, influence);
        double solarFishing = SolarExposure.fishingEffect(preference, orientationExposure, influence, env.getSolar().getMaxWeight());
        WindOrientation windOrientation = FishingWindEffect.classify(sample, orientation);
        double windFishing = FishingWindEffect.effect(windOrientation, env.getWind().getFishingMaxWeight());
        TemperatureEffect temperature = temperatureEffect(sample, context, env.getTemperature());
        double boatPenalty = boatWeatherPenalty.penalty(sample, context);
        double strategyTime = strategyTimeEffect(candidate, instant, context);
        double intrinsic = candidate.score().finalScore();
        double utility = clamp(intrinsic + (strategyTime - candidate.score().breakdown().timeWindowMatch())
                * context.properties().getRanking().getTimeWindowMatch()
                + solarFishing
                + windFishing
                + temperature.value()
                - boatPenalty
                - waitPenaltyValue);
        ScoreBreakdown breakdown = candidate.score().breakdown().withTimeAdjusted(
                intrinsic,
                strategyTime,
                influence.strength(),
                orientationExposure,
                solarFishing,
                windOrientation.name(),
                windFishing,
                temperature.value(),
                boatPenalty,
                waitPenaltyValue,
                utility
        );
        EnvironmentSnapshot snapshot = new EnvironmentSnapshot(
                Double.isNaN(sun.azimuthDeg()) ? null : round(sun.azimuthDeg()),
                Double.isNaN(sun.elevationDeg()) ? null : round(sun.elevationDeg()),
                orientation.shorelineWaterFacingAspect() == null ? null : round(orientation.shorelineWaterFacingAspect()),
                orientation.slopeAspect() == null ? null : round(orientation.slopeAspect()),
                orientation.rawFeatureOrientation() == null ? null : round(orientation.rawFeatureOrientation()),
                orientation.confidence().name(),
                orientation.source(),
                round(influence.strength()),
                influence.source(),
                sample.windFromAzimuth() == null ? null : round(sample.windFromAzimuth()),
                sample.windFlowAzimuth() == null ? null : round(sample.windFlowAzimuth()),
                sample.windSpeedKmh() == null ? null : round(sample.windSpeedKmh()),
                sample.airTemperatureC() == null ? null : round(sample.airTemperatureC()),
                sample.cloudCoverPercent() == null ? null : round(sample.cloudCoverPercent()),
                sample.directRadiation(),
                sample.shortwaveRadiation(),
                preference.name(),
                temperature.source()
        );
        return new Evaluation(utility, breakdown, snapshot, sample, sun, influence, windOrientation);
    }

    public double dwellValue(
            RankedCandidate candidate,
            Instant arrival,
            int dwellMinutes,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation
    ) {
        PlanningProperties.Schedule schedule = context.properties().getSchedule();
        int slot = Math.max(1, schedule.getSlotMinutes());
        int slots = Math.max(1, (int) Math.ceil(dwellMinutes / (double) slot));
        double decay = schedule.getDwellDecay();
        double sum = 0;
        for (int i = 0; i < slots; i++) {
            Instant t = arrival.plusSeconds((long) i * slot * 60L);
            Evaluation evaluation = evaluate(candidate, t, context, weather, orientation, 0);
            sum += evaluation.utility() * Math.pow(decay, i);
        }
        return sum;
    }

    private double strategyTimeEffect(RankedCandidate candidate, Instant instant, PlanningContext context) {
        LocalTime local = TripClock.localTime(instant, context.lake());
        LocalTime from = candidate.spot().getWindowFrom();
        LocalTime to = candidate.spot().getWindowTo();
        if (from != null && to != null && !local.isBefore(from) && local.isBefore(to)) {
            return 1.0;
        }
        if (context.profile() != null) {
            for (StrategyTimeWindow window : context.profile().timeWindows()) {
                if (window.from() != null && window.to() != null
                        && !local.isBefore(window.from()) && local.isBefore(window.to())) {
                    return candidate.spot().isWindowSpecific() ? 0.55 : 0.85;
                }
            }
        }
        return 0.4;
    }

    private TemperatureEffect temperatureEffect(
            WeatherSample sample,
            PlanningContext context,
            PlanningProperties.Environment.Temperature config
    ) {
        if (context.weather() != null && context.weather().waterTemperatureAvailable()
                && context.weather().waterTemperatureC() != null) {
            return new TemperatureEffect(0, "WATER");
        }
        if (sample == null || sample.airTemperatureC() == null) {
            return new TemperatureEffect(0, "UNAVAILABLE");
        }
        double air = sample.airTemperatureC();
        double comfort = 1.0 - Math.min(1.0, Math.abs(air - 18.0) / 20.0);
        double value = (comfort - 0.5) * 2.0 * config.getMaxWeight() * config.getAirProxyWeight();
        return new TemperatureEffect(value, "AIR_PROXY");
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1.5, value));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record Evaluation(
            double utility,
            ScoreBreakdown breakdown,
            EnvironmentSnapshot environment,
            WeatherSample weather,
            SolarPosition sun,
            SolarInfluence solarInfluence,
            WindOrientation windOrientation
    ) {
    }

    private record TemperatureEffect(double value, String source) {
    }
}
