package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.WeatherSnapshot;

import java.time.Instant;

/**
 * Immutable environment resolved before {@code FishingSessionStateBuilder}.
 * Not a JSON $def; later state work may add fields without a contract PR.
 */
public record EnvironmentSnapshot(
        Instant resolvedAt,
        WeatherSnapshot weather,
        boolean refreshed,
        Integer weatherAgeMinutes,
        Double queryLatitudeWgs84,
        Double queryLongitudeWgs84
) {
    /**
     * Compatibility constructor for replay/tests that only have weather.
     */
    public EnvironmentSnapshot(Instant resolvedAt, WeatherSnapshot weather) {
        this(resolvedAt, weather, false, WeatherSnapshotFreshness.ageMinutes(weather, resolvedAt), null, null);
    }
}
