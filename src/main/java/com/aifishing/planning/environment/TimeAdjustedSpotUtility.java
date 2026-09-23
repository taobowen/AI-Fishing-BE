package com.aifishing.planning.environment;

import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.ArrivalStrategyEvaluator;
import com.aifishing.planning.spatial.RequestScoringCache;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.LightPreference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TimeAdjustedSpotUtility {

    private final SolarPositionService solarPositionService;
    private final BoatWeatherPenalty boatWeatherPenalty;
    private final ArrivalStrategyEvaluator arrivalStrategyEvaluator;

    public TimeAdjustedSpotUtility(SolarPositionService solarPositionService, BoatWeatherPenalty boatWeatherPenalty) {
        this(solarPositionService, boatWeatherPenalty, new ArrivalStrategyEvaluator());
    }

    @Autowired
    public TimeAdjustedSpotUtility(
            SolarPositionService solarPositionService,
            BoatWeatherPenalty boatWeatherPenalty,
            ArrivalStrategyEvaluator arrivalStrategyEvaluator
    ) {
        this.solarPositionService = solarPositionService;
        this.boatWeatherPenalty = boatWeatherPenalty;
        this.arrivalStrategyEvaluator = arrivalStrategyEvaluator == null
                ? new ArrivalStrategyEvaluator()
                : arrivalStrategyEvaluator;
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
        RequestScoringCache scoring = RequestScoringCache.current();
        RequestScoringCache.EvaluationKey cacheKey = null;
        if (scoring != null && candidate != null && candidate.spot() != null && candidate.score() != null
                && candidate.score().breakdown() != null) {
            cacheKey = scoring.evaluationKey(
                    candidate, instant, sampleLocation, context, weather, orientation, waitPenaltyValue);
            Evaluation cached = scoring.evaluation(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        Evaluation computed = computeEvaluation(
                candidate, instant, sampleLocation, context, weather, orientation, waitPenaltyValue);
        if (scoring != null && cacheKey != null) {
            scoring.putEvaluation(cacheKey, computed);
        }
        return computed;
    }

    private Evaluation computeEvaluation(
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
        ArrivalStrategyEvaluator.ArrivalStrategy arrival = arrivalStrategyEvaluator.evaluate(candidate.spot(), instant, context);
        LightPreference preference = arrival.lightPreference();
        double orientationExposure = SolarExposure.orientationExposure(sun, orientation, influence);
        double solarFishing = SolarExposure.fishingEffect(preference, orientationExposure, influence, env.getSolar().getMaxWeight());
        WindOrientation windOrientation = FishingWindEffect.classify(sample, orientation);
        double windFishing = FishingWindEffect.effect(windOrientation, env.getWind().getFishingMaxWeight());
        TemperatureEffect temperature = temperatureEffect(sample, context, env.getTemperature());
        double boatPenalty = boatWeatherPenalty.penalty(sample, context);
        PlanningProperties.Ranking weights = context.properties().getRanking();
        ScoreBreakdown prior = candidate.score().breakdown();
        double intrinsic = candidate.score().finalScore();
        double utility = clamp(intrinsic
                + (arrival.strategyMatch() - prior.strategyMatch()) * weights.getStrategyMatch()
                + (arrival.depthMatch() - prior.depthMatch()) * weights.getDepthMatch()
                + (arrival.timeWindowMatch() - prior.timeWindowMatch()) * weights.getTimeWindowMatch()
                + solarFishing
                + windFishing
                + temperature.value()
                - boatPenalty
                - waitPenaltyValue);
        ScoreBreakdown breakdown = candidate.score().breakdown().withTimeAdjusted(
                intrinsic,
                arrival.timeWindowMatch(),
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
