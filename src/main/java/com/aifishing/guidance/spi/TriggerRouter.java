package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.SessionEvent;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;

import java.util.Optional;

/**
 * Maps a raw session event plus current state to an enqueue decision.
 * Empty means do not write outbox. Must not return a bare trigger.
 */
public interface TriggerRouter {

    Optional<TriggerRoutingDecision> route(SessionEvent event, FishingSessionState state);
}
