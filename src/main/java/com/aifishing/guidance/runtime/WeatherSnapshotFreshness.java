package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.WeatherSnapshot;

import java.time.Duration;
import java.time.Instant;

/**
 * Freshness is observed-at age versus {@code app.guidance.weather-snapshot-max-age-minutes}.
 * Redis may later replace the DB lookup inside the resolver only.
 */
public final class WeatherSnapshotFreshness {

    private WeatherSnapshotFreshness() {
    }

    public static boolean isFresh(Instant observedAt, Instant now, Duration maxAge) {
        if (observedAt == null || now == null || maxAge == null || maxAge.isNegative()) {
            return false;
        }
        Duration age = Duration.between(observedAt, now);
        return !age.minus(maxAge).isPositive();
    }

    public static Integer ageMinutes(WeatherSnapshot weather, Instant now) {
        if (weather == null || weather.observedAt() == null || now == null) {
            return null;
        }
        long minutes = Duration.between(weather.observedAt(), now).toMinutes();
        return (int) Math.max(0, minutes);
    }
}
