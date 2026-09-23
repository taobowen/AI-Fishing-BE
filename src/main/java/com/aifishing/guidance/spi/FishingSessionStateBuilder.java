package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.runtime.EnvironmentSnapshot;

import java.util.UUID;

/**
 * Deterministic derivation only. Must not fetch weather or persist.
 */
public interface FishingSessionStateBuilder {

    FishingSessionState build(UUID sessionId, EnvironmentSnapshot environment);
}
