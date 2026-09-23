package com.aifishing.webquota.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.planning.domain.PlanningRun;
import com.aifishing.planning.domain.PlanningRunStatus;
import com.aifishing.planning.dto.GeneratePlanResponse;
import com.aifishing.planning.repo.PlanningRunRepository;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.service.TripPlanAssembler;
import com.aifishing.webquota.domain.UserWebPlanEntitlement;
import com.aifishing.webquota.domain.WebPlanGenerationRequest;
import com.aifishing.webquota.domain.WebPlanQuotaLedger;
import com.aifishing.webquota.domain.WebPlanRequestStatus;
import com.aifishing.webquota.dto.WebPlanEntitlementResponse;
import com.aifishing.webquota.repo.UserWebPlanEntitlementRepository;
import com.aifishing.webquota.repo.WebPlanGenerationRequestRepository;
import com.aifishing.webquota.repo.WebPlanQuotaLedgerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebPlanQuotaService {

    public static final String QUOTA_EXHAUSTED = "WEB_PLAN_QUOTA_EXHAUSTED";
    public static final String IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";
    public static final int DEFAULT_LIMIT = 3;

    private final CurrentUser currentUser;
    private final UserWebPlanEntitlementRepository entitlementRepository;
    private final WebPlanGenerationRequestRepository requestRepository;
    private final WebPlanQuotaLedgerRepository ledgerRepository;
    private final PlanningRunRepository planningRunRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TripWaypointRepository waypointRepository;
    private final TripPlanAssembler assembler;

    public WebPlanQuotaService(
            CurrentUser currentUser,
            UserWebPlanEntitlementRepository entitlementRepository,
            WebPlanGenerationRequestRepository requestRepository,
            WebPlanQuotaLedgerRepository ledgerRepository,
            PlanningRunRepository planningRunRepository,
            TripPlanRepository tripPlanRepository,
            TripWaypointRepository waypointRepository,
            TripPlanAssembler assembler
    ) {
        this.currentUser = currentUser;
        this.entitlementRepository = entitlementRepository;
        this.requestRepository = requestRepository;
        this.ledgerRepository = ledgerRepository;
        this.planningRunRepository = planningRunRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.waypointRepository = waypointRepository;
        this.assembler = assembler;
    }

    public String requireIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException(IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key is required for web plan generation");
        }
        String key = raw.trim();
        if (key.length() > 128) {
            throw new BadRequestException(IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key must be 128 characters or fewer");
        }
        return key;
    }

    @Transactional
    public WebPlanEntitlementResponse current() {
        UserWebPlanEntitlement row = entitlement(currentUser.id());
        return new WebPlanEntitlementResponse(row.getLifetimeLimit(), row.getSuccessfulGenerations(), row.remaining());
    }

    @Transactional
    public Optional<GeneratePlanResponse> replayIfComplete(UUID userId, String idempotencyKey) {
        return requestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .filter(row -> row.getStatus() == WebPlanRequestStatus.COMPLETED && row.getTripPlanId() != null)
                .flatMap(row -> toResponse(row.getPlanningRunId(), row.getTripPlanId()));
    }

    @Transactional
    public Optional<GeneratePlanResponse> replayIfInProgress(UUID userId, String idempotencyKey) {
        return requestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .filter(row -> row.getStatus() == WebPlanRequestStatus.STARTED && row.getPlanningRunId() != null)
                .flatMap(row -> planningRunRepository.findById(row.getPlanningRunId()))
                .filter(run -> run.getStatus() == PlanningRunStatus.RUNNING)
                .map(run -> assembler.toGenerateResponse(run, null, List.of()));
    }

    @Transactional
    public void precheck(UUID userId) {
        UserWebPlanEntitlement row = entitlement(userId);
        if (row.remaining() <= 0) {
            throw new BadRequestException(QUOTA_EXHAUSTED, "You've used your 3 free web plans.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claimAttempt(UUID userId, UUID tripId, String idempotencyKey, UUID planningRunId) {
        Optional<WebPlanGenerationRequest> existing =
                requestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            WebPlanGenerationRequest row = existing.get();
            if (row.getStatus() == WebPlanRequestStatus.COMPLETED && row.getTripPlanId() != null) {
                throw new CompletedIdempotentGeneration(row.getPlanningRunId(), row.getTripPlanId());
            }
            if (row.getStatus() == WebPlanRequestStatus.STARTED && isRunning(row.getPlanningRunId())) {
                throw new InProgressIdempotentGeneration(row.getPlanningRunId());
            }
            row.setTripId(tripId);
            row.setPlanningRunId(planningRunId);
            row.setTripPlanId(null);
            row.setStatus(WebPlanRequestStatus.STARTED);
            requestRepository.saveAndFlush(row);
            return;
        }
        WebPlanGenerationRequest row = new WebPlanGenerationRequest();
        row.setUserId(userId);
        row.setIdempotencyKey(idempotencyKey);
        row.setTripId(tripId);
        row.setPlanningRunId(planningRunId);
        row.setStatus(WebPlanRequestStatus.STARTED);
        try {
            requestRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException ex) {
            WebPlanGenerationRequest raced = requestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> ex);
            if (raced.getStatus() == WebPlanRequestStatus.COMPLETED && raced.getTripPlanId() != null) {
                throw new CompletedIdempotentGeneration(raced.getPlanningRunId(), raced.getTripPlanId());
            }
            if (raced.getStatus() == WebPlanRequestStatus.STARTED && isRunning(raced.getPlanningRunId())) {
                throw new InProgressIdempotentGeneration(raced.getPlanningRunId());
            }
            throw new BadRequestException("GENERATION_IN_PROGRESS", "A plan is already being generated for this request");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consumeOnSuccess(UUID userId, String idempotencyKey, UUID planningRunId, UUID tripPlanId) {
        UserWebPlanEntitlement row = lockEntitlement(userId);
        boolean alreadyCounted = ledgerRepository.existsById(planningRunId);
        if (!alreadyCounted) {
            if (row.remaining() <= 0) {
                throw new BadRequestException(QUOTA_EXHAUSTED, "You've used your 3 free web plans.");
            }
            WebPlanQuotaLedger ledger = new WebPlanQuotaLedger();
            ledger.setPlanningRunId(planningRunId);
            ledger.setUserId(userId);
            ledger.setTripPlanId(tripPlanId);
            try {
                ledgerRepository.saveAndFlush(ledger);
                row.setSuccessfulGenerations(row.getSuccessfulGenerations() + 1);
                entitlementRepository.save(row);
            } catch (DataIntegrityViolationException ignored) {
                // persist retry of the same planning run
            }
        }
        WebPlanGenerationRequest request = requestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseGet(() -> {
                    WebPlanGenerationRequest created = new WebPlanGenerationRequest();
                    created.setUserId(userId);
                    created.setIdempotencyKey(idempotencyKey);
                    return created;
                });
        request.setPlanningRunId(planningRunId);
        request.setTripPlanId(tripPlanId);
        request.setStatus(WebPlanRequestStatus.COMPLETED);
        requestRepository.save(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID userId, String idempotencyKey, UUID planningRunId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        requestRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).ifPresent(row -> {
            if (row.getStatus() != WebPlanRequestStatus.COMPLETED) {
                row.setPlanningRunId(planningRunId);
                row.setStatus(WebPlanRequestStatus.FAILED);
                requestRepository.save(row);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedByPlanningRun(UUID planningRunId) {
        if (planningRunId == null) {
            return;
        }
        requestRepository.findFirstByPlanningRunId(planningRunId).ifPresent(row -> {
            if (row.getStatus() != WebPlanRequestStatus.COMPLETED) {
                row.setStatus(WebPlanRequestStatus.FAILED);
                requestRepository.save(row);
            }
        });
    }

    public Optional<GeneratePlanResponse> toResponse(UUID planningRunId, UUID tripPlanId) {
        if (planningRunId == null || tripPlanId == null) {
            return Optional.empty();
        }
        return planningRunRepository.findById(planningRunId).flatMap(run ->
                tripPlanRepository.findById(tripPlanId).map(plan ->
                        assembler.toGenerateResponse(
                                run,
                                plan,
                                waypointRepository.findByTripPlanIdOrderBySequenceAsc(plan.getId())
                        )));
    }

    public GeneratePlanResponse requireReplay(CompletedIdempotentGeneration replay) {
        return toResponse(replay.planningRunId(), replay.tripPlanId())
                .orElseThrow(() -> new BadRequestException("IDEMPOTENT_PLAN_MISSING", "Previous plan could not be loaded"));
    }

    private boolean isRunning(UUID planningRunId) {
        if (planningRunId == null) {
            return false;
        }
        return planningRunRepository.findById(planningRunId)
                .map(PlanningRun::getStatus)
                .filter(status -> status == PlanningRunStatus.RUNNING)
                .isPresent();
    }

    private UserWebPlanEntitlement entitlement(UUID userId) {
        return entitlementRepository.findById(userId).orElseGet(() -> createEntitlement(userId));
    }

    private UserWebPlanEntitlement lockEntitlement(UUID userId) {
        return entitlementRepository.lockByUserId(userId).orElseGet(() -> {
            createEntitlement(userId);
            return entitlementRepository.lockByUserId(userId).orElseThrow();
        });
    }

    private UserWebPlanEntitlement createEntitlement(UUID userId) {
        UserWebPlanEntitlement created = new UserWebPlanEntitlement();
        created.setUserId(userId);
        created.setSuccessfulGenerations(0);
        created.setLifetimeLimit(DEFAULT_LIMIT);
        try {
            return entitlementRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            return entitlementRepository.findById(userId).orElseThrow(() -> ex);
        }
    }

    public static final class CompletedIdempotentGeneration extends RuntimeException {
        private final UUID planningRunId;
        private final UUID tripPlanId;

        public CompletedIdempotentGeneration(UUID planningRunId, UUID tripPlanId) {
            this.planningRunId = planningRunId;
            this.tripPlanId = tripPlanId;
        }

        public UUID planningRunId() {
            return planningRunId;
        }

        public UUID tripPlanId() {
            return tripPlanId;
        }
    }

    public static final class InProgressIdempotentGeneration extends RuntimeException {
        private final UUID planningRunId;

        public InProgressIdempotentGeneration(UUID planningRunId) {
            this.planningRunId = planningRunId;
        }

        public UUID planningRunId() {
            return planningRunId;
        }
    }
}
