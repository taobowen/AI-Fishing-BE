package com.aifishing.guidance.attribution;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceRejectReason;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.ReflectionClaimKind;
import com.aifishing.guidance.contracts.TriggerRoutingDecision;
import com.aifishing.guidance.dispatch.GuidanceTriggerOutboxService;
import com.aifishing.guidance.persistence.AgentFeedbackEntity;
import com.aifishing.guidance.persistence.AgentFeedbackRepository;
import com.aifishing.guidance.persistence.AgentReflectionEntity;
import com.aifishing.guidance.persistence.AgentReflectionRepository;
import com.aifishing.guidance.persistence.GuidanceTriggerOutboxSource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ReflectionEvaluator {

    private final GuidanceProperties properties;
    private final Clock clock;
    private final OutcomeAttributor outcomeAttributor;
    private final AgentFeedbackRepository feedbackRepository;
    private final AgentReflectionRepository reflectionRepository;
    private final GuidanceTriggerOutboxService triggerOutboxService;

    public ReflectionEvaluator(
            GuidanceProperties properties,
            Clock clock,
            OutcomeAttributor outcomeAttributor,
            AgentFeedbackRepository feedbackRepository,
            AgentReflectionRepository reflectionRepository,
            GuidanceTriggerOutboxService triggerOutboxService
    ) {
        this.properties = properties;
        this.clock = clock;
        this.outcomeAttributor = outcomeAttributor;
        this.feedbackRepository = feedbackRepository;
        this.reflectionRepository = reflectionRepository;
        this.triggerOutboxService = triggerOutboxService;
    }

    public ReflectionEvaluation.Result evaluate(UUID fishingSessionId) {
        if (fishingSessionId == null) {
            return new ReflectionEvaluation.Result(List.of(), false);
        }
        List<ComputedAttribution> attributions = outcomeAttributor.loadComputed(fishingSessionId);
        List<ReflectionEvaluation.FeedbackObservation> feedbacks = new ArrayList<>();
        for (AgentFeedbackEntity row : feedbackRepository.findByFishingSessionIdOrderByOccurredAtAsc(fishingSessionId)) {
            feedbacks.add(new ReflectionEvaluation.FeedbackObservation(
                    row.getId(),
                    row.getDeliveredDecisionId(),
                    row.getStatus(),
                    parseRejectReason(row.getRejectReason()),
                    row.getOccurredAt()
            ));
        }
        List<ReflectionEvaluation.ExistingReflection> existing = new ArrayList<>();
        for (AgentReflectionEntity row : reflectionRepository.findByFishingSessionIdOrderByCreatedAtAsc(fishingSessionId)) {
            existing.add(new ReflectionEvaluation.ExistingReflection(row.getCauseKind(), row.getText()));
        }
        GuidanceProperties.RuntimeMode mode = properties.getRuntimeMode();
        ReflectionEvaluation.Result result = ReflectionEvaluation.evaluate(
                clock.instant(),
                properties.getAttribution(),
                attributions,
                feedbacks,
                existing
        );
        for (ReflectionEvaluation.ComputedReflection reflection : result.reflections()) {
            AgentReflectionEntity entity = new AgentReflectionEntity();
            entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
            entity.setFishingSessionId(fishingSessionId);
            entity.setClaimKind(mode == GuidanceProperties.RuntimeMode.DETERMINISTIC
                    ? ReflectionClaimKind.OBSERVED_FACT
                    : reflection.claimKind());
            entity.setCauseKind(reflection.causeKind());
            entity.setText(reflection.text());
            entity.setCreatedAt(reflection.createdAt() == null ? clock.instant() : reflection.createdAt());
            reflectionRepository.save(entity);
        }
        if (result.consecutiveFailure()) {
            triggerOutboxService.upsert(
                    fishingSessionId,
                    new TriggerRoutingDecision(
                            GuidanceTrigger.CONSECUTIVE_FAILURE,
                            List.of(),
                            List.of("CONSECUTIVE_STRATEGY_FAILURE")
                    ),
                    GuidanceTriggerOutboxSource.EVENT
            );
        }
        return result;
    }

    static GuidanceRejectReason parseRejectReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GuidanceRejectReason.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return GuidanceRejectReason.UNSPECIFIED;
        }
    }
}
