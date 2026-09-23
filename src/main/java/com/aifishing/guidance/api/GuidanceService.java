package com.aifishing.guidance.api;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceCurrentResponse;
import com.aifishing.guidance.contracts.GuidanceDecisionRequest;
import com.aifishing.guidance.contracts.GuidanceFeedbackRequest;
import com.aifishing.guidance.contracts.GuidanceRejectReason;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.LureEvent;
import com.aifishing.guidance.attribution.AdviceLifecycleWriter;
import com.aifishing.guidance.attribution.GuidanceLearningHooks;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.horizon.GuidanceHorizonWriter;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentFeedbackEntity;
import com.aifishing.guidance.persistence.AgentFeedbackRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.runtime.GuidanceFallback;
import com.aifishing.guidance.runtime.OptionalUserInput;
import com.aifishing.guidance.spi.DecisionPersistence;
import com.aifishing.guidance.spi.FishingAgentFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class GuidanceService {

    private final CurrentUser currentUser;
    private final Clock clock;
    private final FishingSessionRepository sessionRepository;
    private final SessionEventWriter sessionEventWriter;
    private final FishingAgentFacade facade;
    private final DecisionPersistence decisionPersistence;
    private final GuidanceHorizonWriter horizonWriter;
    private final AgentDeliveredDecisionRepository deliveredRepository;
    private final AgentRunRepository agentRunRepository;
    private final AgentFeedbackRepository feedbackRepository;
    private final AdviceLifecycleWriter adviceLifecycleWriter;
    private final GuidanceLearningHooks learningHooks;

    public GuidanceService(
            CurrentUser currentUser,
            Clock clock,
            FishingSessionRepository sessionRepository,
            SessionEventWriter sessionEventWriter,
            FishingAgentFacade facade,
            DecisionPersistence decisionPersistence,
            GuidanceHorizonWriter horizonWriter,
            AgentDeliveredDecisionRepository deliveredRepository,
            AgentRunRepository agentRunRepository,
            AgentFeedbackRepository feedbackRepository,
            AdviceLifecycleWriter adviceLifecycleWriter,
            GuidanceLearningHooks learningHooks
    ) {
        this.currentUser = currentUser;
        this.clock = clock;
        this.sessionRepository = sessionRepository;
        this.sessionEventWriter = sessionEventWriter;
        this.facade = facade;
        this.decisionPersistence = decisionPersistence;
        this.horizonWriter = horizonWriter;
        this.deliveredRepository = deliveredRepository;
        this.agentRunRepository = agentRunRepository;
        this.feedbackRepository = feedbackRepository;
        this.adviceLifecycleWriter = adviceLifecycleWriter;
        this.learningHooks = learningHooks;
    }

    @Transactional
    public void recordBite(UUID sessionId, BiteRequest request) {
        requireRequest(request == null ? null : request.clientEventId(), request == null ? null : request.occurredAt());
        sessionEventWriter.onBite(requireOwned(sessionId), request.clientEventId(), request.occurredAt(), request.fishInteractionId());
    }

    @Transactional
    public void recordFishOn(UUID sessionId, FishOnRequest request) {
        requireRequest(request == null ? null : request.clientEventId(), request == null ? null : request.occurredAt());
        if (request.fishInteractionId() == null) {
            throw new BadRequestException("fishInteractionId is required");
        }
        sessionEventWriter.onFishOn(
                requireOwned(sessionId),
                request.clientEventId(),
                request.occurredAt(),
                request.fishInteractionId()
        );
    }

    @Transactional
    public void recordLure(UUID sessionId, LureEventRequest request) {
        if (request == null || request.occurredAt() == null) {
            throw new BadRequestException("occurredAt is required");
        }
        if (request.lureFamily() == null || request.presentation() == null) {
            throw new BadRequestException("lureFamily and presentation are required");
        }
        sessionEventWriter.onLure(
                requireOwned(sessionId),
                request.clientEventId(),
                new LureEvent(
                        GuidanceSchemaVersion.VALUE,
                        request.occurredAt(),
                        request.lureFamily(),
                        request.presentation(),
                        request.depthM(),
                        request.retrieveStyle()
                )
        );
    }

    public GuidanceCurrentResponse requestDecision(UUID sessionId, GuidanceDecisionRequest request) {
        FishingSession session = requireOwned(sessionId);
        if (!session.getGuidanceMode().routesAgent()) {
            throw new BadRequestException("Guidance is not enabled for NAVIGATION_ONLY sessions");
        }
        OptionalUserInput input = request == null
                ? null
                : new OptionalUserInput(request.userNote(), request.clientHints());
        AgentRunResult result = facade.run(session.getId(), GuidanceTrigger.USER_REQUEST, input);
        if (GuidanceFallback.isKillSwitch(result)) {
            DeliveredDecision body = decisionPersistence.current(session.getId())
                    .orElse(result.delivered());
            if (body == null) {
                throw new NotFoundException("Guidance decision not found");
            }
            UUID runId = body.decisionId() != null ? body.decisionId() : result.runId();
            return new GuidanceCurrentResponse(runId, body);
        }
        horizonWriter.writeAfterDelivered(session.getId(), result);
        adviceLifecycleWriter.writeCreated(session.getId(), result);
        if (result.delivered() == null) {
            throw new NotFoundException("Guidance decision not found");
        }
        return new GuidanceCurrentResponse(result.runId(), result.delivered());
    }

    @Transactional(readOnly = true)
    public GuidanceCurrentResponse current(UUID sessionId) {
        FishingSession session = requireOwned(sessionId);
        DeliveredDecision delivered = decisionPersistence.current(session.getId())
                .orElseThrow(() -> new NotFoundException("Guidance decision not found"));
        UUID runId = delivered.decisionId() != null ? delivered.decisionId() : session.getId();
        return new GuidanceCurrentResponse(runId, delivered);
    }

    @Transactional
    public void submitFeedback(UUID sessionId, UUID decisionId, GuidanceFeedbackRequest request) {
        FishingSession session = requireOwned(sessionId);
        if (request == null || request.status() == null) {
            throw new BadRequestException("status is required");
        }
        AgentDeliveredDecisionEntity delivered = requireDelivered(session.getId(), decisionId);
        AgentFeedbackEntity feedback = new AgentFeedbackEntity();
        feedback.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        feedback.setDeliveredDecisionId(delivered.getId());
        feedback.setFishingSessionId(session.getId());
        feedback.setStatus(request.status());
        feedback.setRejectReason(request.rejectReason() == null ? null : request.rejectReason().name());
        feedback.setNote(request.note());
        if (request.status() == FeedbackStatus.REJECTED && request.rejectReason() == null) {
            feedback.setRejectReason(GuidanceRejectReason.UNSPECIFIED.name());
        }
        feedback.setOccurredAt(clock.instant());
        AgentFeedbackEntity saved = feedbackRepository.save(feedback);
        DeliveredDecision decision = toDeliveredDecision(delivered);
        learningHooks.onFeedback(
                session.getId(),
                session.getUserId(),
                delivered,
                decision,
                request.status(),
                request.rejectReason() == null && request.status() == FeedbackStatus.REJECTED
                        ? GuidanceRejectReason.UNSPECIFIED
                        : request.rejectReason(),
                request.note(),
                saved.getId()
        );
    }

    private static DeliveredDecision toDeliveredDecision(AgentDeliveredDecisionEntity delivered) {
        return com.aifishing.guidance.contracts.GuidanceContracts.mapper()
                .convertValue(delivered.getDecision(), DeliveredDecision.class);
    }

    private AgentDeliveredDecisionEntity requireDelivered(UUID sessionId, UUID decisionId) {
        AgentDeliveredDecisionEntity byRun = deliveredRepository.findFirstByRunId(decisionId).orElse(null);
        if (byRun != null && runBelongsToSession(byRun.getRunId(), sessionId)) {
            return byRun;
        }
        AgentDeliveredDecisionEntity byId = deliveredRepository.findById(decisionId).orElse(null);
        if (byId != null && runBelongsToSession(byId.getRunId(), sessionId)) {
            return byId;
        }
        throw new NotFoundException("Guidance decision not found");
    }

    private boolean runBelongsToSession(UUID runId, UUID sessionId) {
        return agentRunRepository.findById(runId)
                .filter(run -> run.getVisibility() != AgentRunVisibility.SHADOW)
                .map(AgentRunEntity::getFishingSessionId)
                .filter(sessionId::equals)
                .isPresent();
    }

    private FishingSession requireOwned(UUID sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
    }

    private static void requireRequest(String clientEventId, java.time.Instant occurredAt) {
        if (clientEventId == null || clientEventId.isBlank()) {
            throw new BadRequestException("clientEventId is required");
        }
        if (occurredAt == null) {
            throw new BadRequestException("occurredAt is required");
        }
    }
}
