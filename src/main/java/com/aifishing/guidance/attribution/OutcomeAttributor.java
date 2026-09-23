package com.aifishing.guidance.attribution;

import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.OutcomeAttributionEntity;
import com.aifishing.guidance.persistence.OutcomeAttributionRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OutcomeAttributor {

    private static final List<SessionEventType> OUTCOME_TYPES = List.of(
            SessionEventType.BITE,
            SessionEventType.FISH_ON,
            SessionEventType.CATCH_CREATED
    );

    private final GuidanceProperties properties;
    private final Clock clock;
    private final AgentDeliveredDecisionRepository deliveredRepository;
    private final SessionEventRepository sessionEventRepository;
    private final CatchEventRepository catchEventRepository;
    private final OutcomeAttributionRepository attributionRepository;
    private final UserActionObserver userActionObserver;

    public OutcomeAttributor(
            GuidanceProperties properties,
            Clock clock,
            AgentDeliveredDecisionRepository deliveredRepository,
            SessionEventRepository sessionEventRepository,
            CatchEventRepository catchEventRepository,
            OutcomeAttributionRepository attributionRepository,
            UserActionObserver userActionObserver
    ) {
        this.properties = properties;
        this.clock = clock;
        this.deliveredRepository = deliveredRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.catchEventRepository = catchEventRepository;
        this.attributionRepository = attributionRepository;
        this.userActionObserver = userActionObserver;
    }

    public List<ComputedAttribution> attribute(UUID fishingSessionId) {
        if (fishingSessionId == null) {
            return List.of();
        }
        List<DeliveredSnapshot> decisions = loadDecisions(fishingSessionId);
        List<ObservedUserAction> actions = userActionObserver.load(fishingSessionId);
        List<OutcomeSignal> signals = loadSignals(fishingSessionId);
        List<ComputedAttribution> computed = OutcomeAttributionCalculator.compute(
                clock.instant(),
                properties.getAttribution(),
                decisions,
                actions,
                signals
        );
        for (ComputedAttribution row : computed) {
            upsert(fishingSessionId, row);
        }
        return computed;
    }

    public List<DeliveredSnapshot> loadDecisions(UUID fishingSessionId) {
        List<DeliveredSnapshot> snapshots = new ArrayList<>();
        for (AgentDeliveredDecisionEntity row : deliveredRepository.findByFishingSessionIdOrderByCreatedAtAsc(fishingSessionId)) {
            DeliveredDecision decision = GuidanceContracts.mapper().convertValue(row.getDecision(), DeliveredDecision.class);
            snapshots.add(new DeliveredSnapshot(row.getId(), row.getRunId(), row.getCreatedAt(), decision));
        }
        return snapshots;
    }

    private List<OutcomeSignal> loadSignals(UUID fishingSessionId) {
        List<OutcomeSignal> signals = new ArrayList<>();
        List<SessionEventEntity> events = sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(
                fishingSessionId,
                OUTCOME_TYPES
        );
        Map<String, UUID> catchInteraction = catchInteractionIds(events);
        for (SessionEventEntity event : events) {
            OutcomeKind kind = switch (event.getType()) {
                case BITE -> OutcomeKind.BITE;
                case FISH_ON -> OutcomeKind.FISH_ON;
                case CATCH_CREATED -> OutcomeKind.FISH_ON;
                default -> null;
            };
            if (kind == null) {
                continue;
            }
            if (event.getType() == SessionEventType.CATCH_CREATED) {
                continue;
            }
            signals.add(new OutcomeSignal(event.getId(), event.getOccurredAt(), kind, event.getFishInteractionId()));
        }
        for (CatchEvent catchEvent : catchEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(fishingSessionId)) {
            if (catchEvent.getStatus() == CatchStatus.VOIDED) {
                continue;
            }
            UUID interactionId = catchInteraction.get(catchEvent.getClientCatchId());
            if (interactionId == null) {
                interactionId = SessionEventWriter.interactionIdFromClientCatch(catchEvent.getClientCatchId());
            }
            OutcomeKind kind = switch (catchEvent.getOutcome()) {
                case LANDED -> OutcomeKind.CATCH_LANDED;
                case LOST -> OutcomeKind.CATCH_LOST;
                default -> null;
            };
            if (kind == null) {
                continue;
            }
            signals.add(new OutcomeSignal(catchEvent.getId(), catchEvent.getOccurredAt(), kind, interactionId));
        }
        return signals;
    }

    private static Map<String, UUID> catchInteractionIds(List<SessionEventEntity> events) {
        Map<String, UUID> ids = new java.util.HashMap<>();
        for (SessionEventEntity event : events) {
            if (event.getType() != SessionEventType.CATCH_CREATED || event.getPayload() == null) {
                continue;
            }
            Object clientCatchId = event.getPayload().get("clientCatchId");
            if (clientCatchId != null && event.getFishInteractionId() != null) {
                ids.put(String.valueOf(clientCatchId), event.getFishInteractionId());
            }
        }
        return ids;
    }

    private void upsert(UUID fishingSessionId, ComputedAttribution row) {
        OutcomeAttributionEntity entity = row.fishInteractionId() == null
                ? attributionRepository
                        .findByDeliveredDecisionIdAndAttributionDimensionAndRecommendationRoleAndOutcomeKindAndFishInteractionIdIsNull(
                                row.deliveredDecisionId(),
                                row.attributionDimension(),
                                row.recommendationRole(),
                                row.outcomeKind()
                        )
                        .orElse(null)
                : attributionRepository
                        .findByDeliveredDecisionIdAndAttributionDimensionAndRecommendationRoleAndFishInteractionId(
                                row.deliveredDecisionId(),
                                row.attributionDimension(),
                                row.recommendationRole(),
                                row.fishInteractionId()
                        )
                        .orElse(null);
        if (entity == null) {
            entity = new OutcomeAttributionEntity();
            entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
            entity.setFishingSessionId(fishingSessionId);
        } else if (AttributionWindows.outcomeRank(entity.getOutcomeKind()) > AttributionWindows.outcomeRank(row.outcomeKind())) {
            return;
        }
        entity.setOutcomeEventId(row.outcomeEventId());
        entity.setFishInteractionId(row.fishInteractionId());
        entity.setDeliveredDecisionId(row.deliveredDecisionId());
        entity.setAttributionDimension(row.attributionDimension());
        entity.setFollowedRecommendation(row.followedRecommendation());
        entity.setRecommendationRole(row.recommendationRole());
        entity.setOutcomeKind(row.outcomeKind());
        entity.setWindowKind(row.windowKind());
        entity.setConfidence(row.confidence() == null
                ? null
                : BigDecimal.valueOf(row.confidence()).setScale(3, RoundingMode.HALF_UP));
        entity.setAttributedAt(row.attributedAt() == null ? clock.instant() : row.attributedAt());
        attributionRepository.save(entity);
    }

    public List<ComputedAttribution> loadComputed(UUID fishingSessionId) {
        List<ComputedAttribution> rows = new ArrayList<>();
        for (OutcomeAttributionEntity entity : attributionRepository.findByFishingSessionIdOrderByAttributedAtAsc(fishingSessionId)) {
            rows.add(new ComputedAttribution(
                    entity.getOutcomeEventId(),
                    entity.getFishInteractionId(),
                    entity.getDeliveredDecisionId(),
                    entity.getAttributionDimension(),
                    entity.isFollowedRecommendation(),
                    entity.getRecommendationRole(),
                    entity.getOutcomeKind(),
                    entity.getWindowKind(),
                    entity.getConfidence() == null ? null : entity.getConfidence().doubleValue(),
                    entity.getAttributedAt()
            ));
        }
        return rows;
    }
}
