package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;

import java.util.Optional;

/**
 * Time-derived triggers (heartbeat). Must not take a raw {@code SessionEvent}.
 * Empty means do not write outbox.
 */
public interface DerivedTriggerEvaluator {

    Optional<TriggerRoutingDecision> evaluate(FishingSessionState state);
}
