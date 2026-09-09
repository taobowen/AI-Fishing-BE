package com.aifishing.feedback.performance;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.feedback.effort.domain.EffortSegmentType;
import com.aifishing.feedback.effort.domain.FishingEffortSegment;
import com.aifishing.feedback.effort.repo.FishingEffortSegmentRepository;
import com.aifishing.feedback.performance.dto.LakeEmpiricalSummaryResponse;
import com.aifishing.feedback.performance.dto.SessionPerformanceResponse;
import com.aifishing.feedback.performance.dto.WaypointPerformanceDto;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EmpiricalPerformanceService {

    private final FishingSessionRepository sessionRepository;
    private final SessionWaypointProgressRepository progressRepository;
    private final FishingEffortSegmentRepository segmentRepository;
    private final CatchEventRepository catchEventRepository;
    private final TripRepository tripRepository;
    private final FeedbackProperties feedbackProperties;

    public EmpiricalPerformanceService(
            FishingSessionRepository sessionRepository,
            SessionWaypointProgressRepository progressRepository,
            FishingEffortSegmentRepository segmentRepository,
            CatchEventRepository catchEventRepository,
            TripRepository tripRepository,
            FeedbackProperties feedbackProperties
    ) {
        this.sessionRepository = sessionRepository;
        this.progressRepository = progressRepository;
        this.segmentRepository = segmentRepository;
        this.catchEventRepository = catchEventRepository;
        this.tripRepository = tripRepository;
        this.feedbackProperties = feedbackProperties;
    }

    @Transactional
    public SessionPerformanceResponse recompute(UUID sessionId) {
        FishingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
        SessionPerformanceResponse response = compute(session);
        Map<String, Object> summary = session.getSummary() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(session.getSummary());
        summary.put("fishingEffortSeconds", response.fishingEffortSeconds());
        summary.put("travelSeconds", response.travelSeconds());
        summary.put("unknownSeconds", response.unknownSeconds());
        summary.put("landedCount", response.landedCount());
        summary.put("lostCount", response.lostCount());
        summary.put("pendingCount", response.pendingCount());
        summary.put("rawLandedCpue", response.rawLandedCpue());
        summary.put("bestWaypointId", response.bestWaypointId() == null ? null : response.bestWaypointId().toString());
        session.setSummary(summary);
        return response;
    }

    @Transactional(readOnly = true)
    public SessionPerformanceResponse get(UUID sessionId, UUID userId) {
        FishingSession session = userId == null
                ? sessionRepository.findById(sessionId).orElseThrow(() -> new NotFoundException("Fishing session not found"))
                : sessionRepository.findByIdAndUserId(sessionId, userId)
                        .orElseThrow(() -> new NotFoundException("Fishing session not found"));
        return compute(session);
    }

    @Transactional(readOnly = true)
    public LakeEmpiricalSummaryResponse lakeSummary(UUID lakeId) {
        List<FishingSession> sessions = sessionRepository.findAll().stream()
                .filter(session -> session.getStatus() == FishingSessionStatus.COMPLETED)
                .toList();
        long completed = 0;
        long landed = 0;
        long fishingSeconds = 0;
        com.aifishing.common.enums.FishSpecies species = null;
        for (FishingSession session : sessions) {
            Trip trip = tripRepository.findById(session.getTripId()).orElse(null);
            if (trip == null || !lakeId.equals(trip.getLakeId())) {
                continue;
            }
            completed++;
            species = trip.getPrimaryTargetSpecies();
            fishingSeconds += secondsOf(session.getId(), EffortSegmentType.FISHING);
            landed += catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(
                            session.getId(), CatchStatus.ACTIVE).stream()
                    .filter(item -> item.getOutcome() == CatchOutcome.LANDED)
                    .count();
        }
        double hours = fishingSeconds / 3600.0;
        return new LakeEmpiricalSummaryResponse(
                lakeId,
                species,
                completed,
                landed,
                round3(hours),
                ShrinkageScorer.landedCpue((int) landed, hours)
        );
    }

    private SessionPerformanceResponse compute(FishingSession session) {
        List<FishingEffortSegment> segments =
                segmentRepository.findByFishingSessionIdOrderByStartedAtAsc(session.getId());
        int fishing = 0;
        int travel = 0;
        int unknown = 0;
        Map<UUID, Integer> fishingByWaypoint = new HashMap<>();
        for (FishingEffortSegment segment : segments) {
            if (segment.getSegmentType() == EffortSegmentType.FISHING) {
                fishing += segment.getDurationSeconds();
                if (segment.getTripWaypointId() != null) {
                    fishingByWaypoint.merge(segment.getTripWaypointId(), segment.getDurationSeconds(), Integer::sum);
                }
            } else if (segment.getSegmentType() == EffortSegmentType.TRAVEL) {
                travel += segment.getDurationSeconds();
            } else {
                unknown += segment.getDurationSeconds();
            }
        }
        List<CatchEvent> catches = catchEventRepository.findByFishingSessionIdAndStatusOrderByOccurredAtAsc(
                session.getId(), CatchStatus.ACTIVE);
        int landed = 0;
        int lost = 0;
        int pending = 0;
        Map<UUID, int[]> catchByWaypoint = new HashMap<>();
        for (CatchEvent catchEvent : catches) {
            if (catchEvent.getOutcome() == CatchOutcome.LANDED) {
                landed++;
            } else if (catchEvent.getOutcome() == CatchOutcome.LOST) {
                lost++;
            } else if (catchEvent.getOutcome() == CatchOutcome.PENDING || catchEvent.getOutcome() == CatchOutcome.UNKNOWN) {
                pending++;
            }
            if (catchEvent.getTripWaypointId() != null) {
                int[] counts = catchByWaypoint.computeIfAbsent(catchEvent.getTripWaypointId(), key -> new int[2]);
                if (catchEvent.getOutcome() == CatchOutcome.LANDED) {
                    counts[0]++;
                } else if (catchEvent.getOutcome() == CatchOutcome.LOST) {
                    counts[1]++;
                }
            }
        }
        List<SessionWaypointProgress> progress =
                progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        List<WaypointPerformanceDto> waypoints = new ArrayList<>();
        UUID bestId = null;
        double bestScore = -1;
        int minSeconds = feedbackProperties.getPerformance().getMinEffortMinutes() * 60;
        for (SessionWaypointProgress row : progress) {
            int effort = fishingByWaypoint.getOrDefault(row.getTripWaypointId(), 0);
            int[] counts = catchByWaypoint.getOrDefault(row.getTripWaypointId(), new int[2]);
            double hours = effort / 3600.0;
            Double rawCpue = ShrinkageScorer.landedCpue(counts[0], hours);
            ShrinkageScorer.Result smoothed = ShrinkageScorer.score(
                    counts[0], hours, feedbackProperties.getPerformance());
            waypoints.add(new WaypointPerformanceDto(
                    row.getTripWaypointId(),
                    row.getSequence(),
                    effort,
                    counts[0],
                    counts[1],
                    rawCpue,
                    smoothed.historicalPerformance()
            ));
            if (effort >= minSeconds && smoothed.historicalPerformance() > bestScore) {
                bestScore = smoothed.historicalPerformance();
                bestId = row.getTripWaypointId();
            }
        }
        double sessionHours = fishing / 3600.0;
        return new SessionPerformanceResponse(
                session.getId(),
                fishing,
                travel,
                unknown,
                session.getTotalPausedSeconds(),
                landed,
                lost,
                pending,
                ShrinkageScorer.landedCpue(landed, sessionHours),
                bestId,
                bestId == null ? null : bestScore,
                waypoints
        );
    }

    private int secondsOf(UUID sessionId, EffortSegmentType type) {
        return segmentRepository.findByFishingSessionIdAndSegmentTypeOrderByStartedAtAsc(sessionId, type).stream()
                .mapToInt(FishingEffortSegment::getDurationSeconds)
                .sum();
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
