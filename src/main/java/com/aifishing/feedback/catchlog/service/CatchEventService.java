package com.aifishing.feedback.catchlog.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.dto.CatchEventResponse;
import com.aifishing.feedback.catchlog.dto.CreateCatchRequest;
import com.aifishing.feedback.catchlog.dto.UpdateCatchRequest;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.feedback.performance.EmpiricalPerformanceService;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class CatchEventService {

    private final CurrentUser currentUser;
    private final Clock clock;
    private final GeoMapper geoMapper;
    private final FishingSessionRepository sessionRepository;
    private final TripRepository tripRepository;
    private final TripPlanRepository tripPlanRepository;
    private final CatchEventRepository catchEventRepository;
    private final CatchAssociationService associationService;
    private final EmpiricalPerformanceService performanceService;

    public CatchEventService(
            CurrentUser currentUser,
            Clock clock,
            GeoMapper geoMapper,
            FishingSessionRepository sessionRepository,
            TripRepository tripRepository,
            TripPlanRepository tripPlanRepository,
            CatchEventRepository catchEventRepository,
            CatchAssociationService associationService,
            EmpiricalPerformanceService performanceService
    ) {
        this.currentUser = currentUser;
        this.clock = clock;
        this.geoMapper = geoMapper;
        this.sessionRepository = sessionRepository;
        this.tripRepository = tripRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.catchEventRepository = catchEventRepository;
        this.associationService = associationService;
        this.performanceService = performanceService;
    }

    @Transactional
    public CatchEventResponse create(UUID sessionId, CreateCatchRequest request) {
        if (request == null || request.clientCatchId() == null || request.clientCatchId().isBlank()) {
            throw new BadRequestException("clientCatchId is required");
        }
        if (request.occurredAt() == null) {
            throw new BadRequestException("occurredAt is required");
        }
        FishingSession session = requireOwnedSession(sessionId);
        requireCatchWindow(session, request.occurredAt());
        return catchEventRepository
                .findByFishingSessionIdAndClientCatchId(session.getId(), request.clientCatchId())
                .map(this::toResponse)
                .orElseGet(() -> persistNew(session, request));
    }

    @Transactional(readOnly = true)
    public List<CatchEventResponse> list(UUID sessionId) {
        FishingSession session = requireOwnedSession(sessionId);
        return catchEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(session.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatchEventResponse get(UUID catchId) {
        return toResponse(requireOwnedCatch(catchId));
    }

    @Transactional
    public CatchEventResponse update(UUID catchId, UpdateCatchRequest request) {
        CatchEvent catchEvent = requireOwnedCatch(catchId);
        FishingSession session = requireOwnedSession(catchEvent.getFishingSessionId());
        requireCatchMutable(session, catchEvent.getOccurredAt());
        if (catchEvent.getStatus() == CatchStatus.VOIDED) {
            throw new BadRequestException("Cannot update a VOIDED catch");
        }
        if (request != null) {
            if (request.outcome() != null) {
                catchEvent.setOutcome(request.outcome());
            }
            if (request.species() != null) {
                catchEvent.setSpecies(request.species());
            }
            if (request.lengthCm() != null) {
                catchEvent.setLengthCm(decimal(request.lengthCm(), 2));
            }
            if (request.weightKg() != null) {
                catchEvent.setWeightKg(decimal(request.weightKg(), 3));
            }
            if (request.techniqueType() != null) {
                catchEvent.setTechniqueType(request.techniqueType());
            }
            if (request.lureName() != null) {
                catchEvent.setLureName(request.lureName());
            }
            if (request.notes() != null) {
                catchEvent.setNotes(request.notes());
            }
        }
        CatchEvent saved = catchEventRepository.save(catchEvent);
        recomputePerformanceIfEnded(session);
        return toResponse(saved);
    }

    @Transactional
    public CatchEventResponse voidCatch(UUID catchId) {
        CatchEvent catchEvent = requireOwnedCatch(catchId);
        FishingSession session = requireOwnedSession(catchEvent.getFishingSessionId());
        requireCatchMutable(session, catchEvent.getOccurredAt());
        if (catchEvent.getStatus() != CatchStatus.VOIDED) {
            catchEvent.setStatus(CatchStatus.VOIDED);
            catchEventRepository.save(catchEvent);
            recomputePerformanceIfEnded(session);
        }
        return toResponse(catchEvent);
    }

    private CatchEventResponse persistNew(FishingSession session, CreateCatchRequest request) {
        Trip trip = tripRepository.findById(session.getTripId())
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        TripPlan plan = session.getTripPlanId() == null
                ? null
                : tripPlanRepository.findById(session.getTripPlanId()).orElse(null);
        Point location = geoMapper.toPoint(request.location());
        CatchAssociationService.Association association =
                associationService.associate(session, request.waypointId(), request.occurredAt(), location);

        CatchEvent catchEvent = new CatchEvent();
        catchEvent.setUserId(session.getUserId());
        catchEvent.setFishingSessionId(session.getId());
        catchEvent.setTripId(session.getTripId());
        catchEvent.setTripPlanId(session.getTripPlanId());
        catchEvent.setTripWaypointId(association.tripWaypointId());
        catchEvent.setLakeFeatureId(association.lakeFeatureId());
        catchEvent.setFishingTargetId(association.fishingTargetId());
        catchEvent.setZoneId(association.zoneId());
        catchEvent.setSubtargetId(association.subtargetId());
        catchEvent.setClientCatchId(request.clientCatchId());
        catchEvent.setOccurredAt(request.occurredAt());
        catchEvent.setReceivedAt(clock.instant());
        catchEvent.setLocation(location);
        catchEvent.setGpsAccuracyM(decimal(request.accuracyM(), 2));
        catchEvent.setAssociationMethod(association.method());
        catchEvent.setDistanceToWaypointM(decimal(association.distanceM(), 2));
        catchEvent.setStatus(CatchStatus.ACTIVE);
        catchEvent.setOutcome(CatchOutcome.PENDING);
        catchEvent.setSpecies(request.species());
        catchEvent.setLengthCm(decimal(request.lengthCm(), 2));
        catchEvent.setWeightKg(decimal(request.weightKg(), 3));
        catchEvent.setTechniqueType(request.techniqueType());
        catchEvent.setLureName(request.lureName());
        catchEvent.setNotes(request.notes());
        catchEvent.setPlannedFeatureType(association.plannedFeatureType());
        catchEvent.setPrimaryTargetSpecies(trip.getPrimaryTargetSpecies());
        catchEvent.setStrategyRunId(plan == null ? null : plan.getStrategyRunId());
        CatchEvent saved = catchEventRepository.save(catchEvent);
        recomputePerformanceIfEnded(session);
        return toResponse(saved);
    }

    private void recomputePerformanceIfEnded(FishingSession session) {
        if (session.getStatus() == FishingSessionStatus.COMPLETED) {
            performanceService.recompute(session.getId());
        }
    }

    private static void requireCatchWindow(FishingSession session, java.time.Instant occurredAt) {
        if (session.getStatus() == FishingSessionStatus.CANCELLED) {
            throw new BadRequestException("Session is already CANCELLED");
        }
        if (occurredAt.isBefore(session.getStartedAt())) {
            throw new BadRequestException("Catch occurredAt is before session start");
        }
        if (session.getEndedAt() != null && occurredAt.isAfter(session.getEndedAt())) {
            throw new BadRequestException("Catch occurredAt is after session end");
        }
        if (session.getStatus() == FishingSessionStatus.COMPLETED && session.getEndedAt() == null) {
            throw new BadRequestException("Completed session is missing endedAt");
        }
    }

    private static void requireCatchMutable(FishingSession session, java.time.Instant occurredAt) {
        requireCatchWindow(session, occurredAt);
    }

    private FishingSession requireOwnedSession(UUID sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
    }

    private CatchEvent requireOwnedCatch(UUID catchId) {
        return catchEventRepository.findByIdAndUserId(catchId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Catch not found"));
    }

    private CatchEventResponse toResponse(CatchEvent catchEvent) {
        return new CatchEventResponse(
                catchEvent.getId(),
                catchEvent.getClientCatchId(),
                catchEvent.getFishingSessionId(),
                catchEvent.getTripId(),
                catchEvent.getTripPlanId(),
                catchEvent.getTripWaypointId(),
                catchEvent.getLakeFeatureId(),
                catchEvent.getFishingTargetId(),
                catchEvent.getZoneId(),
                catchEvent.getSubtargetId(),
                catchEvent.getAlongTrackFraction() == null ? null : catchEvent.getAlongTrackFraction().doubleValue(),
                catchEvent.getOccurredAt(),
                geoMapper.toDto(catchEvent.getLocation()),
                catchEvent.getGpsAccuracyM() == null ? null : catchEvent.getGpsAccuracyM().doubleValue(),
                catchEvent.getAssociationMethod(),
                catchEvent.getDistanceToWaypointM() == null ? null : catchEvent.getDistanceToWaypointM().doubleValue(),
                catchEvent.getStatus(),
                catchEvent.getOutcome(),
                catchEvent.getSpecies(),
                catchEvent.getLengthCm() == null ? null : catchEvent.getLengthCm().doubleValue(),
                catchEvent.getWeightKg() == null ? null : catchEvent.getWeightKg().doubleValue(),
                catchEvent.getTechniqueType(),
                catchEvent.getLureName(),
                catchEvent.getNotes(),
                catchEvent.getPlannedFeatureType(),
                catchEvent.getPrimaryTargetSpecies()
        );
    }

    private static BigDecimal decimal(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
