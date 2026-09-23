package com.aifishing.guidance.empirical;

import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.performance.ShrinkageScorer;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.CompassDirection;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.HistoricalPerformance;
import com.aifishing.guidance.contracts.SeasonBucket;
import com.aifishing.guidance.contracts.TimeBucket;
import com.aifishing.guidance.contracts.WindBucket;
import com.aifishing.guidance.contracts.WindDirectionBucket;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Single version for empirical bucketing and derived rates. Bump when season/time/wind
 * boundaries or shrinkage / rate formulas change. Old contribution rows stay; rebuilds
 * write only the new version.
 */
public final class EmpiricalAlgorithm {

    public static final int VERSION = 1;

    private static final int[] TIME_BOUNDARIES = {5, 7, 11, 14, 17, 20};

    private EmpiricalAlgorithm() {
    }

    public static ZoneId zoneOf(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            return ZoneId.of("UTC");
        }
        return ZoneId.of(timeZoneId);
    }

    public static SeasonBucket seasonBucket(Instant instant, ZoneId zone) {
        int month = LocalDateTime.ofInstant(instant, zone).getMonthValue();
        if (month <= 2 || month == 12) {
            return SeasonBucket.WINTER;
        }
        if (month <= 5) {
            return SeasonBucket.SPRING;
        }
        if (month <= 8) {
            return SeasonBucket.SUMMER;
        }
        return SeasonBucket.FALL;
    }

    public static TimeBucket timeBucket(Instant instant, ZoneId zone) {
        int hour = LocalDateTime.ofInstant(instant, zone).getHour();
        if (hour < 5 || hour >= 20) {
            return TimeBucket.NIGHT;
        }
        if (hour < 7) {
            return TimeBucket.DAWN;
        }
        if (hour < 11) {
            return TimeBucket.MORNING;
        }
        if (hour < 14) {
            return TimeBucket.MIDDAY;
        }
        if (hour < 17) {
            return TimeBucket.AFTERNOON;
        }
        return TimeBucket.DUSK;
    }

    public static Instant nextTimeBucketStart(Instant instant, ZoneId zone) {
        ZonedDateTime local = instant.atZone(zone);
        int hour = local.getHour();
        for (int boundary : TIME_BOUNDARIES) {
            if (hour < boundary) {
                return local.withHour(boundary).withMinute(0).withSecond(0).withNano(0).toInstant();
            }
        }
        return local.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant();
    }

    public static WindBucket windBucket(Double speedKph) {
        if (speedKph == null) {
            return null;
        }
        if (speedKph < 10.0) {
            return WindBucket.CALM;
        }
        if (speedKph < 25.0) {
            return WindBucket.MODERATE;
        }
        return WindBucket.STRONG;
    }

    public static WindDirectionBucket windDirection(CompassDirection direction) {
        if (direction == null) {
            return null;
        }
        return WindDirectionBucket.valueOf(direction.name());
    }

    public static WindDirectionBucket windDirection(Collection<CompassDirection> window) {
        if (window == null || window.isEmpty()) {
            return null;
        }
        Set<CompassDirection> distinct = new LinkedHashSet<>();
        for (CompassDirection direction : window) {
            if (direction != null) {
                distinct.add(direction);
            }
        }
        if (distinct.isEmpty()) {
            return null;
        }
        if (distinct.size() > 1) {
            return WindDirectionBucket.VARIABLE;
        }
        return windDirection(distinct.iterator().next());
    }

    public static Derived derive(EmpiricalRawCounts raw, GuidanceProperties.Empirical cfg) {
        EmpiricalRawCounts counts = raw == null ? EmpiricalRawCounts.ZERO : raw;
        long seconds = Math.max(0, counts.fishingEffortSeconds());
        Double hours = seconds <= 0 ? null : seconds / 3600.0;
        Double biteRate = hours == null ? null : counts.biteCount() / hours;
        Double fishOnRate = hours == null ? null : counts.fishOnCount() / hours;
        Double cpue = hours == null ? null : counts.landedCount() / hours;
        Double landingRate = counts.fishOnCount() <= 0 ? null : counts.landedCount() / (double) counts.fishOnCount();
        Double effortMinutes = seconds / 60.0;
        ShrinkageScorer.Result score = ShrinkageScorer.score(
                counts.fishOnCount(),
                seconds / 3600.0,
                toPerformance(cfg)
        );
        return new Derived(
                effortMinutes,
                biteRate,
                fishOnRate,
                landingRate,
                cpue,
                score.historicalPerformance(),
                score.evidenceConfidence()
        );
    }

    public static HistoricalPerformance toHistorical(
            EmpiricalGrain grain,
            EmpiricalRawCounts raw,
            Derived derived,
            Instant updatedAt
    ) {
        int landed = raw.landedCount();
        int waypoints = raw.contributingSessionWaypointCount();
        return new HistoricalPerformance(
                GuidanceSchemaVersion.VALUE,
                grain.lakeId(),
                grain.zoneId(),
                grain.tripWaypointId(),
                grain.species(),
                grain.seasonBucket(),
                grain.timeBucket(),
                grain.structure(),
                grain.windBucket(),
                grain.windDirectionBucket(),
                grain.lureFamily(),
                derived.effortMinutes(),
                landed,
                derived.cpue(),
                derived.smoothedScore(),
                waypoints,
                derived.sampleConfidence(),
                updatedAt,
                raw.fishingEffortSeconds(),
                raw.biteCount(),
                raw.fishOnCount(),
                landed,
                waypoints,
                derived.biteRate(),
                derived.fishOnRate(),
                derived.landingRate(),
                grain.algorithmVersion()
        );
    }

    private static FeedbackProperties.Performance toPerformance(GuidanceProperties.Empirical cfg) {
        FeedbackProperties.Performance performance = new FeedbackProperties.Performance();
        if (cfg == null) {
            return performance;
        }
        performance.setMinEffortMinutes(cfg.getMinEffortMinutes());
        performance.setPriorEffortHours(cfg.getPriorEffortHours());
        performance.setPriorLandedPerHour(cfg.getPriorFishOnPerHour());
        return performance;
    }

    public record Derived(
            Double effortMinutes,
            Double biteRate,
            Double fishOnRate,
            Double landingRate,
            Double cpue,
            Double smoothedScore,
            Double sampleConfidence
    ) {
    }
}
