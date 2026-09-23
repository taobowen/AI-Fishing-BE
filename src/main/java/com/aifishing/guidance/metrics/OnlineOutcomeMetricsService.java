package com.aifishing.guidance.metrics;

import com.aifishing.feedback.effort.domain.EffortSegmentType;
import com.aifishing.feedback.effort.domain.FishingEffortSegment;
import com.aifishing.feedback.effort.repo.FishingEffortSegmentRepository;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.attribution.AttributionWindows;
import com.aifishing.guidance.attribution.DeliveredSnapshot;
import com.aifishing.guidance.attribution.ObservedUserAction;
import com.aifishing.guidance.attribution.OutcomeAttributionCalculator;
import com.aifishing.guidance.attribution.OutcomeAttributor;
import com.aifishing.guidance.attribution.RecommendedSlice;
import com.aifishing.guidance.attribution.ResolvedAttributionWindow;
import com.aifishing.guidance.attribution.UserActionObserver;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.OnlineMetricGrain;
import com.aifishing.guidance.persistence.AgentFeedbackEntity;
import com.aifishing.guidance.persistence.AgentFeedbackRepository;
import com.aifishing.guidance.persistence.GuidanceOnlineMetricRollupEntity;
import com.aifishing.guidance.persistence.GuidanceOnlineMetricRollupRepository;
import com.aifishing.guidance.persistence.OutcomeAttributionEntity;
import com.aifishing.guidance.persistence.OutcomeAttributionRepository;
import com.aifishing.guidance.persistence.UserActionEventEntity;
import com.aifishing.guidance.persistence.UserActionEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OnlineOutcomeMetricsService {

    private final OutcomeAttributionRepository attributionRepository;
    private final AgentFeedbackRepository feedbackRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final UserActionObserver userActionObserver;
    private final OutcomeAttributor outcomeAttributor;
    private final FishingEffortSegmentRepository effortSegmentRepository;
    private final GuidanceOnlineMetricRollupRepository rollupRepository;
    private final OnlineOutcomeGauges gauges;
    private final GuidanceProperties properties;
    private final Clock clock;

    public OnlineOutcomeMetricsService(
            OutcomeAttributionRepository attributionRepository,
            AgentFeedbackRepository feedbackRepository,
            UserActionEventRepository userActionEventRepository,
            UserActionObserver userActionObserver,
            OutcomeAttributor outcomeAttributor,
            FishingEffortSegmentRepository effortSegmentRepository,
            GuidanceOnlineMetricRollupRepository rollupRepository,
            OnlineOutcomeGauges gauges,
            GuidanceProperties properties,
            Clock clock
    ) {
        this.attributionRepository = attributionRepository;
        this.feedbackRepository = feedbackRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.userActionObserver = userActionObserver;
        this.outcomeAttributor = outcomeAttributor;
        this.effortSegmentRepository = effortSegmentRepository;
        this.rollupRepository = rollupRepository;
        this.gauges = gauges;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<OnlineOutcomeSnapshot> rollup(OnlineMetricGrain grain, Instant windowStart, Instant windowEnd) {
        Instant start = windowStart == null ? OnlineMetricWindows.start(clock.instant(), grain) : windowStart;
        Instant end = windowEnd == null ? OnlineMetricWindows.end(start, grain) : windowEnd;
        OnlineMetricGrain resolvedGrain = grain == null ? OnlineMetricGrain.HOUR : grain;

        List<OnlineOutcomeEvent> attributions = loadAttributions(start, end);
        List<FeedbackObservation> feedbacks = loadFeedback(start, end);
        SessionBundle sessions = loadSessions(attributions, feedbacks, start, end);
        List<OnlineOutcomeSnapshot> snapshots = new ArrayList<>();
        snapshots.add(persistAndPublish(resolvedGrain, start, end, null, attributions, feedbacks, sessions));
        for (AttributionDimension dimension : AttributionDimension.values()) {
            snapshots.add(persistAndPublish(resolvedGrain, start, end, dimension, attributions, feedbacks, sessions));
        }
        return snapshots;
    }

    private OnlineOutcomeSnapshot persistAndPublish(
            OnlineMetricGrain grain,
            Instant windowStart,
            Instant windowEnd,
            AttributionDimension dimension,
            List<OnlineOutcomeEvent> attributions,
            List<FeedbackObservation> feedbacks,
            SessionBundle sessions
    ) {
        OnlineOutcomeSnapshot snapshot = OnlineGuidanceMetricsCalculator.compute(
                windowStart,
                windowEnd,
                dimension,
                attributions,
                feedbacks,
                sessions.follows(),
                sessions.windows(),
                sessions.effort(),
                OnlineOutcomeSafetyCounts.empty()
        );
        upsert(grain, snapshot);
        gauges.publish(grain, snapshot);
        return snapshot;
    }

    private void upsert(OnlineMetricGrain grain, OnlineOutcomeSnapshot snapshot) {
        OnlineOutcomeRawCounts raw = snapshot.raw();
        GuidanceOnlineMetricRollupEntity entity = rollupRepository
                .findByWindowStartAndWindowEndAndGrainAndAttributionDimension(
                        snapshot.metrics().windowStart(),
                        snapshot.metrics().windowEnd(),
                        grain,
                        snapshot.metrics().attributionDimension()
                )
                .orElseGet(GuidanceOnlineMetricRollupEntity::new);
        if (entity.getId() == null) {
            entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        }
        entity.setWindowStart(snapshot.metrics().windowStart());
        entity.setWindowEnd(snapshot.metrics().windowEnd());
        entity.setGrain(grain);
        entity.setAttributionDimension(snapshot.metrics().attributionDimension());
        entity.setFishOnSuccessCount(raw.fishOnSuccessCount());
        entity.setBiteSignalOnlyCount(raw.biteSignalOnlyCount());
        entity.setNoFishSignalCount(raw.noFishSignalCount());
        entity.setNotFollowedCount(raw.notFollowedCount());
        entity.setUnattributedCount(raw.unattributedCount());
        entity.setFollowedRecommendationCount(raw.followedRecommendationCount());
        entity.setExplicitAcceptedCount(raw.explicitAcceptedCount());
        entity.setExplicitPartialCount(raw.explicitPartialCount());
        entity.setRejectCount(raw.rejectCount());
        entity.setOverrideCount(raw.overrideCount());
        entity.setFollowThroughEligibleCount(raw.followThroughEligibleCount());
        entity.setOverrideEligibleCount(raw.overrideEligibleCount());
        entity.setFeedbackCount(raw.feedbackCount());
        OnlineOutcomeSafetyCounts safety = raw.safetyOrEmpty();
        entity.setCandidateCount(safety.candidateCount());
        entity.setDeliveredCount(safety.deliveredCount());
        entity.setCandidateUnsafeCount(safety.candidateUnsafeCount());
        entity.setCandidateInvalidWaypointCount(safety.candidateInvalidWaypointCount());
        entity.setValidatorInterceptionCount(safety.validatorInterceptionCount());
        entity.setUnsafeDeliveredCount(safety.unsafeDeliveredCount());
        entity.setInvalidDeliveredWaypointCount(safety.invalidDeliveredWaypointCount());
        entity.setEffectiveFishingEffortSeconds(raw.effectiveFishingEffortSeconds());
        rollupRepository.save(entity);
    }

    private List<OnlineOutcomeEvent> loadAttributions(Instant start, Instant end) {
        List<OnlineOutcomeEvent> events = new ArrayList<>();
        for (OutcomeAttributionEntity row : attributionRepository
                .findByAttributedAtGreaterThanEqualAndAttributedAtLessThanOrderByAttributedAtAsc(start, end)) {
            events.add(new OnlineOutcomeEvent(
                    row.getFishingSessionId(),
                    row.getDeliveredDecisionId(),
                    row.getAttributionDimension(),
                    row.getRecommendationRole(),
                    row.getFishInteractionId(),
                    row.getOutcomeKind(),
                    row.isFollowedRecommendation(),
                    row.getAttributedAt()
            ));
        }
        return events;
    }

    private List<FeedbackObservation> loadFeedback(Instant start, Instant end) {
        List<FeedbackObservation> feedbacks = new ArrayList<>();
        for (AgentFeedbackEntity row : feedbackRepository
                .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(start, end)) {
            feedbacks.add(new FeedbackObservation(
                    row.getFishingSessionId(),
                    row.getDeliveredDecisionId(),
                    row.getStatus(),
                    row.getOccurredAt()
            ));
        }
        return feedbacks;
    }

    private SessionBundle loadSessions(
            List<OnlineOutcomeEvent> attributions,
            List<FeedbackObservation> feedbacks,
            Instant start,
            Instant end
    ) {
        Set<UUID> sessionIds = new HashSet<>();
        for (OnlineOutcomeEvent event : attributions) {
            if (event.fishingSessionId() != null) {
                sessionIds.add(event.fishingSessionId());
            }
        }
        for (FeedbackObservation feedback : feedbacks) {
            if (feedback.fishingSessionId() != null) {
                sessionIds.add(feedback.fishingSessionId());
            }
        }
        for (UserActionEventEntity action : userActionEventRepository
                .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(start, end)) {
            if (action.getFishingSessionId() != null) {
                sessionIds.add(action.getFishingSessionId());
            }
        }
        if (sessionIds.isEmpty()) {
            return new SessionBundle(List.of(), List.of(), List.of());
        }
        List<FollowObservation> follows = new ArrayList<>();
        List<RecommendationEffortWindow> windows = new ArrayList<>();
        for (UUID sessionId : sessionIds) {
            List<DeliveredSnapshot> decisions = outcomeAttributor.loadDecisions(sessionId);
            List<ObservedUserAction> actions = userActionObserver.load(sessionId);
            follows.addAll(followsOf(decisions, actions));
            for (ResolvedAttributionWindow window : OutcomeAttributionCalculator.resolveWindows(
                    clock.instant(),
                    properties.getAttribution(),
                    decisions,
                    actions
            )) {
                windows.add(new RecommendationEffortWindow(
                        sessionId,
                        window.deliveredDecisionId(),
                        window.dimension(),
                        window.start(),
                        window.end()
                ));
            }
        }
        List<EffortInterval> effort = new ArrayList<>();
        for (FishingEffortSegment segment : effortSegmentRepository
                .findByFishingSessionIdInAndSegmentTypeOrderByStartedAtAsc(sessionIds, EffortSegmentType.FISHING)) {
            effort.add(new EffortInterval(segment.getFishingSessionId(), segment.getStartedAt(), segment.getEndedAt()));
        }
        return new SessionBundle(follows, windows, effort);
    }

    static List<FollowObservation> followsOf(List<DeliveredSnapshot> decisions, List<ObservedUserAction> actions) {
        List<FollowObservation> follows = new ArrayList<>();
        for (ObservedUserAction action : actions == null ? List.<ObservedUserAction>of() : actions) {
            if (action == null || action.deliveredDecisionId() == null) {
                continue;
            }
            AttributionDimension dimension = dimensionOf(decisions, action);
            if (dimension == null) {
                continue;
            }
            follows.add(new FollowObservation(
                    action.deliveredDecisionId(),
                    dimension,
                    action.followedRecommendation(),
                    !action.followedRecommendation() && action.actualAction() != null
            ));
        }
        return follows;
    }

    private static AttributionDimension dimensionOf(List<DeliveredSnapshot> decisions, ObservedUserAction action) {
        for (DeliveredSnapshot snapshot : decisions == null ? List.<DeliveredSnapshot>of() : decisions) {
            if (!action.deliveredDecisionId().equals(snapshot.deliveredEntityId())) {
                continue;
            }
            for (RecommendedSlice slice : AttributionWindows.slices(snapshot)) {
                if (slice.role() == action.recommendationRole()) {
                    return slice.dimension();
                }
            }
        }
        return AttributionWindows.dimensionOf(action.actualAction()).orElse(null);
    }

    public static OnlineOutcomeRawCounts rawFrom(GuidanceOnlineMetricRollupEntity entity) {
        return new OnlineOutcomeRawCounts(
                clamp(entity.getFishOnSuccessCount()),
                clamp(entity.getBiteSignalOnlyCount()),
                clamp(entity.getNoFishSignalCount()),
                clamp(entity.getNotFollowedCount()),
                clamp(entity.getUnattributedCount()),
                clamp(entity.getFollowedRecommendationCount()),
                clamp(entity.getExplicitAcceptedCount()),
                clamp(entity.getExplicitPartialCount()),
                clamp(entity.getRejectCount()),
                clamp(entity.getOverrideCount()),
                clamp(entity.getFeedbackCount()),
                clamp(entity.getFollowThroughEligibleCount()),
                clamp(entity.getOverrideEligibleCount()),
                entity.getEffectiveFishingEffortSeconds(),
                0,
                0,
                new OnlineOutcomeSafetyCounts(
                        entity.getCandidateCount(),
                        entity.getDeliveredCount(),
                        entity.getCandidateUnsafeCount(),
                        entity.getCandidateInvalidWaypointCount(),
                        entity.getValidatorInterceptionCount(),
                        entity.getUnsafeDeliveredCount(),
                        entity.getInvalidDeliveredWaypointCount()
                )
        );
    }

    public Optional<OnlineOutcomeSnapshot> derivePersisted(
            Instant windowStart,
            Instant windowEnd,
            OnlineMetricGrain grain,
            AttributionDimension dimension
    ) {
        return rollupRepository
                .findByWindowStartAndWindowEndAndGrainAndAttributionDimension(windowStart, windowEnd, grain, dimension)
                .map(entity -> {
                    OnlineOutcomeRawCounts raw = rawFrom(entity);
                    return new OnlineOutcomeSnapshot(
                            OnlineGuidanceMetricsCalculator.derive(windowStart, windowEnd, dimension, raw),
                            new LandingAnalytics(raw.landedCount(), raw.lostCount()),
                            raw
                    );
                });
    }

    private static int clamp(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < 0) {
            return 0;
        }
        return (int) value;
    }

    private record SessionBundle(
            List<FollowObservation> follows,
            List<RecommendationEffortWindow> windows,
            List<EffortInterval> effort
    ) {
    }
}
