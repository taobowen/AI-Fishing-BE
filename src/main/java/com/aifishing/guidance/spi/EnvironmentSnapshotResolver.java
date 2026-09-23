package com.aifishing.guidance.spi;

import com.aifishing.guidance.runtime.EnvironmentSnapshot;

import java.util.UUID;

/**
 * Resolves a fresh immutable environment before {@code FishingSessionStateBuilder}.
 * Weather I/O and {@code weather_snapshots} writes stay here (Redis later, same type).
 */
public interface EnvironmentSnapshotResolver {

    EnvironmentSnapshot resolve(UUID sessionId);
}
