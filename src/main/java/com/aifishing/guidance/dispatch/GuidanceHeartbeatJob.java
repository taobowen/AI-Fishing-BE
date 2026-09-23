package com.aifishing.guidance.dispatch;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.guidance.contracts.EventSource;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.SessionEvent;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxSource;
import com.aifishing.guidance.runtime.EnvironmentSnapshot;
import com.aifishing.guidance.spi.DerivedTriggerEvaluator;
import com.aifishing.guidance.spi.FishingSessionStateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * Low-frequency heartbeat. Evaluates NO_BITE threshold crossing only.
 */
@Component
public class GuidanceHeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(GuidanceHeartbeatJob.class);

    private final FishingSessionRepository sessionRepository;
    private final FishingSessionStateBuilder stateBuilder;
    private final DerivedTriggerEvaluator evaluator;
    private final NoBiteCrossingGuard crossingGuard;
    private final GuidanceTriggerOutboxService outboxService;
    private final SessionEventWriter sessionEventWriter;
    private final Clock clock;

    public GuidanceHeartbeatJob(
            FishingSessionRepository sessionRepository,
            FishingSessionStateBuilder stateBuilder,
            DerivedTriggerEvaluator evaluator,
            NoBiteCrossingGuard crossingGuard,
            GuidanceTriggerOutboxService outboxService,
            SessionEventWriter sessionEventWriter,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.stateBuilder = stateBuilder;
        this.evaluator = evaluator;
        this.crossingGuard = crossingGuard;
        this.outboxService = outboxService;
        this.sessionEventWriter = sessionEventWriter;
        this.clock = clock;
    }

    public int tick() {
        int enqueued = 0;
        for (FishingSession session : sessionRepository.findByStatusAndActivityState(
                FishingSessionStatus.ACTIVE, FishingActivityState.FISHING
        )) {
            try {
                if (!session.getGuidanceMode().routesAgent()) {
                    continue;
                }
                if (evaluateSession(session)) {
                    enqueued++;
                }
            } catch (RuntimeException ex) {
                log.warn("Guidance heartbeat failed for session {}: {}", session.getId(), ex.getMessage());
            }
        }
        return enqueued;
    }

    private boolean evaluateSession(FishingSession session) {
        EnvironmentSnapshot environment = new EnvironmentSnapshot(clock.instant(), null);
        FishingSessionState state = stateBuilder.build(session.getId(), environment);
        Optional<TriggerRoutingDecision> decision = evaluator.evaluate(state);
        if (decision.isEmpty() || !crossingGuard.shouldEnqueue(session, state, decision.get())) {
            return false;
        }
        outboxService.upsert(session.getId(), decision.get(), GuidanceTriggerOutboxSource.HEARTBEAT);
        sessionEventWriter.writeAudit(
                session.getId(),
                new SessionEvent(
                        GuidanceSchemaVersion.VALUE,
                        SessionEventType.NO_BITE,
                        clock.instant(),
                        EventSource.DERIVED,
                        "no-bite:" + crossingGuard.lastResetAt(session).toEpochMilli(),
                        Map.of("noBiteMinutes", state.fishing() == null ? 0 : state.fishing().noBiteMinutes()),
                        null
                )
        );
        return true;
    }
}
