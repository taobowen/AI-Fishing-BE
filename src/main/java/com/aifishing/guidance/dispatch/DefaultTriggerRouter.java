package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.SessionEvent;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxRepository;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.spi.TriggerRouter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw events mostly stay off the LLM path. Only derived decisions are returned.
 */
@Component
public class DefaultTriggerRouter implements TriggerRouter {

    private final GuidanceProperties properties;
    private final Clock clock;
    private final SessionEventRepository sessionEventRepository;
    private final AgentRunRepository agentRunRepository;
    private final GuidanceTriggerOutboxRepository outboxRepository;

    public DefaultTriggerRouter(
            GuidanceProperties properties,
            Clock clock,
            SessionEventRepository sessionEventRepository,
            AgentRunRepository agentRunRepository,
            GuidanceTriggerOutboxRepository outboxRepository
    ) {
        this.properties = properties;
        this.clock = clock;
        this.sessionEventRepository = sessionEventRepository;
        this.agentRunRepository = agentRunRepository;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public Optional<TriggerRoutingDecision> route(SessionEvent event, FishingSessionState state) {
        if (event == null || event.type() == null) {
            return Optional.empty();
        }
        return switch (event.type()) {
            case WAYPOINT_ENTERED -> Optional.of(decision(
                    GuidanceTrigger.WAYPOINT_REACHED,
                    "WAYPOINT_ENTERED"
            ));
            case WAYPOINT_LEFT -> waypointLeft(event);
            case FISH_ON -> fishOn(event, state);
            case BITE -> repeatedBite(event, state);
            case SAFETY_ALERT -> Optional.of(decision(
                    GuidanceTrigger.SAFETY_STATE_CHANGED,
                    "SAFETY_ALERT"
            ));
            case USER_STARTED_AD_HOC_FISHING -> Optional.of(decision(
                    GuidanceTrigger.USER_STARTED_AD_HOC_FISHING,
                    "USER_STARTED_AD_HOC_FISHING"
            ));
            case USER_ENDED_AD_HOC_FISHING -> Optional.of(decision(
                    GuidanceTrigger.USER_ENDED_AD_HOC_FISHING,
                    "USER_ENDED_AD_HOC_FISHING"
            ));
            default -> Optional.empty();
        };
    }

    private Optional<TriggerRoutingDecision> waypointLeft(SessionEvent event) {
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        if (Boolean.TRUE.equals(asBoolean(payload.get("skipped")))) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(asBoolean(payload.get("completed"))) || payload.get("waypointId") != null) {
            return Optional.of(decision(GuidanceTrigger.PLAN_STEP_COMPLETED, "WAYPOINT_COMPLETED"));
        }
        return Optional.empty();
    }

    private Optional<TriggerRoutingDecision> fishOn(SessionEvent event, FishingSessionState state) {
        UUID sessionId = sessionId(event, state);
        if (sessionId != null && withinCooldown(sessionId, GuidanceTrigger.FISH_ON, properties.getTriggers().getFishOnCooldownMinutes())) {
            return Optional.empty();
        }
        return Optional.of(decision(GuidanceTrigger.FISH_ON, "FISH_ON"));
    }

    private Optional<TriggerRoutingDecision> repeatedBite(SessionEvent event, FishingSessionState state) {
        UUID sessionId = sessionId(event, state);
        if (sessionId == null) {
            return Optional.empty();
        }
        int window = properties.getTriggers().getRepeatedBiteWindowMinutes();
        int required = properties.getTriggers().getRepeatedBiteCount();
        Instant since = clock.instant().minus(Duration.ofMinutes(window));
        int bites = sessionEventRepository
                .findByFishingSessionIdAndTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                        sessionId, SessionEventType.BITE, since)
                .size();
        if (bites < required) {
            return Optional.empty();
        }
        if (withinCooldown(sessionId, GuidanceTrigger.REPEATED_BITE_PATTERN, window)) {
            return Optional.empty();
        }
        return Optional.of(decision(GuidanceTrigger.REPEATED_BITE_PATTERN, "REPEATED_BITE_PATTERN"));
    }

    private boolean withinCooldown(UUID sessionId, GuidanceTrigger trigger, int cooldownMinutes) {
        if (cooldownMinutes <= 0) {
            return false;
        }
        Instant since = clock.instant().minus(Duration.ofMinutes(cooldownMinutes));
        if (!outboxRepository
                .findByFishingSessionIdAndPrimaryTriggerAndCreatedAtGreaterThanEqual(sessionId, trigger, since)
                .isEmpty()) {
            return true;
        }
        for (GuidanceTriggerOutboxEntity row : outboxRepository.findByFishingSessionIdAndCreatedAtGreaterThanEqual(sessionId, since)) {
            if (row.getRelatedTriggers() != null && row.getRelatedTriggers().contains(trigger)) {
                return true;
            }
        }
        for (AgentRunEntity run : agentRunRepository.findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(
                sessionId, AgentRunVisibility.PRODUCTION
        )) {
            if (run.getTrigger() == trigger && run.getStartedAt() != null && !run.getStartedAt().isBefore(since)) {
                return true;
            }
        }
        return false;
    }

    private static UUID sessionId(SessionEvent event, FishingSessionState state) {
        if (state != null && state.session() != null) {
            return state.session().sessionId();
        }
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        Object raw = payload.get("fishingSessionId");
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static TriggerRoutingDecision decision(GuidanceTrigger primary, String reason) {
        return new TriggerRoutingDecision(primary, List.of(), List.of(reason));
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }
}
