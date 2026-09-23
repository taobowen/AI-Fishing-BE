package com.aifishing.planning.spatial;

import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.environment.LocalOrientation;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.environment.TimeIndexedWeather;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.weather.WeatherContext;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Exact scoring results for one Generate request. Absent unless {@link #open()}
 * ran on this thread. Identical inputs reuse the original result object.
 */
public final class RequestScoringCache {

    private static final ThreadLocal<RequestScoringCache> CURRENT = new ThreadLocal<>();

    private final Map<String, Object> packages = new HashMap<>();
    private final Map<String, Object> plans = new HashMap<>();
    private final Map<EvaluationKey, TimeAdjustedSpotUtility.Evaluation> evaluations = new HashMap<>();
    private final Map<AlongKey, Double> alongPaths = new HashMap<>();
    private int packageHits;
    private int packageMisses;
    private int planHits;
    private int planMisses;
    private int evaluationHits;
    private int evaluationMisses;
    private int alongHits;
    private int alongMisses;
    private PlanningContext scopedContext;
    private TimeIndexedWeather scopedWeather;
    private Scope scoped;

    private RequestScoringCache() {
    }

    public static void open() {
        CURRENT.set(new RequestScoringCache());
    }

    public static void close() {
        CURRENT.remove();
    }

    public static RequestScoringCache current() {
        return CURRENT.get();
    }

    public Object packageResult(String key) {
        Object value = packages.get(key);
        if (value == null) {
            packageMisses++;
            return null;
        }
        packageHits++;
        return value;
    }

    public void putPackage(String key, Object value) {
        if (key != null && value != null) {
            packages.put(key, value);
        }
    }

    public Object planResult(String key) {
        Object value = plans.get(key);
        if (value == null) {
            planMisses++;
            return null;
        }
        planHits++;
        return value;
    }

    public void putPlan(String key, Object value) {
        if (key != null && value != null) {
            plans.put(key, value);
        }
    }

    public TimeAdjustedSpotUtility.Evaluation evaluation(EvaluationKey key) {
        TimeAdjustedSpotUtility.Evaluation value = evaluations.get(key);
        if (value == null) {
            evaluationMisses++;
            return null;
        }
        evaluationHits++;
        return value;
    }

    public void putEvaluation(EvaluationKey key, TimeAdjustedSpotUtility.Evaluation value) {
        if (key != null && value != null) {
            evaluations.put(key, value);
        }
    }

    public Double alongPath(AlongKey key) {
        Double value = alongPaths.get(key);
        if (value == null) {
            alongMisses++;
            return null;
        }
        alongHits++;
        return value;
    }

    public void putAlongPath(AlongKey key, double value) {
        if (key != null) {
            alongPaths.put(key, value);
        }
    }

    public EvaluationKey evaluationKey(
            RankedCandidate candidate,
            Instant instant,
            Point sampleLocation,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation,
            double waitPenalty
    ) {
        Point location = sampleLocation == null ? candidate.spot().getLocation() : sampleLocation;
        ScoreBreakdown prior = candidate.score().breakdown();
        return new EvaluationKey(
                scope(context, weather),
                candidate.spot().planningIdentity(),
                instant == null ? 0L : instant.toEpochMilli(),
                instant != null,
                location == null ? 0L : Double.doubleToLongBits(location.getX()),
                location == null ? 0L : Double.doubleToLongBits(location.getY()),
                location != null,
                orientation == null ? LocalOrientation.unknown() : orientation,
                Double.doubleToLongBits(waitPenalty),
                Double.doubleToLongBits(candidate.score().finalScore()),
                Double.doubleToLongBits(prior.strategyMatch()),
                Double.doubleToLongBits(prior.depthMatch()),
                Double.doubleToLongBits(prior.timeWindowMatch())
        );
    }

    public AlongKey alongKey(
            RankedCandidate candidate,
            Instant arrival,
            int fishingMinutes,
            Point from,
            Point to,
            Geometry path,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation
    ) {
        boolean staticSamples = candidate.spot().getStaticSamples() != null
                && !candidate.spot().getStaticSamples().isEmpty();
        return new AlongKey(
                scope(context, weather),
                candidate.spot().planningIdentity(),
                arrival == null ? 0L : arrival.toEpochMilli(),
                arrival != null,
                fishingMinutes,
                pointBits(from, true),
                pointBits(from, false),
                from != null,
                pointBits(to, true),
                pointBits(to, false),
                to != null,
                staticSamples,
                staticSamples ? 0L : pathHash(path),
                orientation == null ? LocalOrientation.unknown() : orientation,
                Double.doubleToLongBits(context.properties().getSchedule().getDwellDecay()),
                Double.doubleToLongBits(candidate.score().finalScore()),
                Double.doubleToLongBits(candidate.score().breakdown().strategyMatch()),
                Double.doubleToLongBits(candidate.score().breakdown().depthMatch()),
                Double.doubleToLongBits(candidate.score().breakdown().timeWindowMatch())
        );
    }

    public int packageHits() {
        return packageHits + planHits;
    }

    public int packageMisses() {
        return packageMisses + planMisses;
    }

    public int utilityHits() {
        return evaluationHits + alongHits;
    }

    public int utilityMisses() {
        return evaluationMisses + alongMisses;
    }

    public int alongHits() {
        return alongHits;
    }

    public int evaluationHits() {
        return evaluationHits;
    }

    /**
     * Package results and along-path utilities that were not recomputed.
     * Evaluation hits inside an along-path miss are counted separately.
     */
    public int avoidedComputeCount() {
        return packageHits() + alongHits;
    }

    public static double hitRate(int hits, int misses) {
        int total = hits + misses;
        if (total <= 0) {
            return 0;
        }
        return Math.round(hits * 1000.0 / total) / 10.0;
    }

    private Scope scope(PlanningContext context, TimeIndexedWeather weather) {
        if (context == scopedContext && weather == scopedWeather && scoped != null) {
            return scoped;
        }
        scopedContext = context;
        scopedWeather = weather;
        scoped = Scope.capture(context, weather);
        return scoped;
    }

    private static long pointBits(Point point, boolean x) {
        if (point == null) {
            return 0L;
        }
        return Double.doubleToLongBits(x ? point.getX() : point.getY());
    }

    private static long pathHash(Geometry path) {
        if (path == null) {
            return 0L;
        }
        Coordinate[] coordinates = path.getCoordinates();
        long hash = coordinates.length;
        for (Coordinate coordinate : coordinates) {
            hash = hash * 31 + Double.doubleToLongBits(coordinate.x);
            hash = hash * 31 + Double.doubleToLongBits(coordinate.y);
        }
        return hash;
    }

    public record EvaluationKey(
            Scope scope,
            UUID spotId,
            long instantEpochMilli,
            boolean instantPresent,
            long locationX,
            long locationY,
            boolean locationPresent,
            LocalOrientation orientation,
            long waitBits,
            long intrinsicBits,
            long priorStrategyBits,
            long priorDepthBits,
            long priorWindowBits
    ) {
    }

    public record AlongKey(
            Scope scope,
            UUID spotId,
            long arrivalEpochMilli,
            boolean arrivalPresent,
            int fishingMinutes,
            long fromX,
            long fromY,
            boolean fromPresent,
            long toX,
            long toY,
            boolean toPresent,
            boolean staticSamples,
            long pathHash,
            LocalOrientation orientation,
            long decayBits,
            long intrinsicBits,
            long priorStrategyBits,
            long priorDepthBits,
            long priorWindowBits
    ) {
    }

    public record Scope(
            UUID strategyRunId,
            int profileHash,
            long weatherRetrievedEpoch,
            boolean weatherPresent,
            boolean waterTemperatureAvailable,
            long waterTemperatureBits,
            String timeZone,
            String fishingMode,
            long strategyWeightBits,
            long depthWeightBits,
            long timeWindowWeightBits,
            long depthToleranceBits,
            long solarMaxBits,
            long solarLowBits,
            long solarHighBits,
            long solarOvercastBits,
            long solarClearBits,
            long windFishingMaxBits,
            long windPenaltyBits,
            long windHardRejectBits,
            long temperatureMaxBits,
            long airProxyBits,
            long sampleAlongBits,
            int maxSamples
    ) {
        private static Scope capture(PlanningContext context, TimeIndexedWeather weather) {
            WeatherContext snapshot = weather == null ? null : weather.snapshot();
            if (snapshot == null && context != null) {
                snapshot = context.weather();
            }
            WeatherContext stored = context == null ? null : context.weather();
            PlanningProperties properties = context == null ? null : context.properties();
            PlanningProperties.Ranking ranking = properties == null ? null : properties.getRanking();
            PlanningProperties.Environment environment = properties == null ? null : properties.getEnvironment();
            PlanningProperties.Environment.Solar solar = environment == null ? null : environment.getSolar();
            PlanningProperties.Environment.Wind wind = environment == null ? null : environment.getWind();
            PlanningProperties.Environment.Temperature temperature = environment == null ? null : environment.getTemperature();
            PlanningProperties.Safety safety = properties == null ? null : properties.getSafety();
            PlanningProperties.Spatial spatial = properties == null ? null : properties.getSpatial();
            Double water = stored == null ? null : stored.waterTemperatureC();
            return new Scope(
                    context == null || context.strategyRun() == null ? null : context.strategyRun().getId(),
                    Objects.hashCode(context == null ? null : context.profile()),
                    snapshot == null || snapshot.retrievedAt() == null ? 0L : snapshot.retrievedAt().toEpochMilli(),
                    snapshot != null,
                    stored != null && stored.waterTemperatureAvailable(),
                    water == null ? 0L : Double.doubleToLongBits(water),
                    String.valueOf(TripClock.zoneId(context)),
                    context == null || context.fishingMode() == null ? "" : context.fishingMode().name(),
                    bits(ranking == null ? 0 : ranking.getStrategyMatch()),
                    bits(ranking == null ? 0 : ranking.getDepthMatch()),
                    bits(ranking == null ? 0 : ranking.getTimeWindowMatch()),
                    bits(properties == null ? 0 : properties.getCandidates().getFallbackDepthToleranceM()),
                    bits(solar == null ? 0 : solar.getMaxWeight()),
                    bits(solar == null ? 0 : solar.getLowRadiationThreshold()),
                    bits(solar == null ? 0 : solar.getHighRadiationThreshold()),
                    bits(solar == null ? 0 : solar.getOvercastCloudPercent()),
                    bits(solar == null ? 0 : solar.getClearCloudPercent()),
                    bits(wind == null ? 0 : wind.getFishingMaxWeight()),
                    bits(safety == null ? 0 : safety.getWindPenaltyKmh()),
                    bits(safety == null ? 0 : safety.getWindHardRejectKmh()),
                    bits(temperature == null ? 0 : temperature.getMaxWeight()),
                    bits(temperature == null ? 0 : temperature.getAirProxyWeight()),
                    bits(spatial == null ? 0 : spatial.getSampleAlongM()),
                    spatial == null ? 0 : spatial.getMaxSamplesPerGeometry()
            );
        }

        private static long bits(double value) {
            return Double.doubleToLongBits(value);
        }
    }
}
