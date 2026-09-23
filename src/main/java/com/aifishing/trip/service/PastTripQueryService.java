package com.aifishing.trip.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.lake.service.LakeCardImageResolver;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.trip.api.PastTripResponse;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PastTripQueryService {

    private static final Set<TripPlanStatus> VISIBLE_PLANS =
            EnumSet.of(TripPlanStatus.GENERATED, TripPlanStatus.ACCEPTED);
    private static final Set<WaypointProgressStatus> VISITED = EnumSet.of(
            WaypointProgressStatus.ARRIVED,
            WaypointProgressStatus.FISHING,
            WaypointProgressStatus.COMPLETED
    );

    private final CurrentUser currentUser;
    private final Clock clock;
    private final TripRepository tripRepository;
    private final LakeRepository lakeRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final FishingSessionRepository sessionRepository;
    private final SessionWaypointProgressRepository progressRepository;
    private final LakeCardImageResolver cardImageResolver;

    public PastTripQueryService(
            CurrentUser currentUser,
            Clock clock,
            TripRepository tripRepository,
            LakeRepository lakeRepository,
            TripPlanRepository tripPlanRepository,
            TripWaypointRepository tripWaypointRepository,
            FishingSessionRepository sessionRepository,
            SessionWaypointProgressRepository progressRepository,
            LakeCardImageResolver cardImageResolver
    ) {
        this.currentUser = currentUser;
        this.clock = clock;
        this.tripRepository = tripRepository;
        this.lakeRepository = lakeRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.sessionRepository = sessionRepository;
        this.progressRepository = progressRepository;
        this.cardImageResolver = cardImageResolver;
    }

    @Transactional(readOnly = true)
    public List<PastTripResponse> listPast(YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        List<Trip> trips = tripRepository.findOwnedBetween(currentUser.id(), monthStart.minusDays(1), monthEnd);
        Instant now = clock.instant();
        Map<UUID, Lake> lakes = lakeRepository.findAllById(
                trips.stream().map(Trip::getLakeId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Lake::getId, lake -> lake));

        List<Trip> past = new ArrayList<>();
        Map<UUID, TripClock.Window> windows = new HashMap<>();
        for (Trip trip : trips) {
            Lake lake = lakes.get(trip.getLakeId());
            TripClock.Window window = TripClock.resolve(trip, lake);
            LocalDate startDay = window.start().toLocalDate();
            LocalDate endDay = window.end().toLocalDate();
            boolean intersectsMonth = !endDay.isBefore(monthStart) && !startDay.isAfter(monthEnd);
            if (intersectsMonth && window.endAt().isBefore(now)) {
                past.add(trip);
                windows.put(trip.getId(), window);
            }
        }
        if (past.isEmpty()) {
            return List.of();
        }

        Map<UUID, TripPlan> plans = latestPlans(past.stream().map(Trip::getId).toList());
        Map<UUID, List<FishingSession>> sessionsByTrip = sessionRepository
                .findByUserIdAndTripIdIn(currentUser.id(), past.stream().map(Trip::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(FishingSession::getTripId));
        Map<UUID, Long> waypointCounts = waypointCounts(plans.values().stream().map(TripPlan::getId).toList());
        Map<UUID, List<SessionWaypointProgress>> progressBySession = progress(
                sessionsByTrip.values().stream().flatMap(List::stream).map(FishingSession::getId).toList()
        );
        Map<UUID, String> images = cardImageResolver.urlsFor(lakes.values());

        List<PastTripResponse> cards = new ArrayList<>();
        for (Trip trip : past) {
            TripClock.Window window = windows.get(trip.getId());
            Lake lake = lakes.get(trip.getLakeId());
            TripPlan plan = plans.get(trip.getId());
            List<FishingSession> sessions = sessionsByTrip.getOrDefault(trip.getId(), List.of());
            FishingSession latest = sessions.stream()
                    .max(Comparator.comparing(FishingSession::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            FishingSession completed = sessions.stream()
                    .filter(session -> session.getStatus() == FishingSessionStatus.COMPLETED)
                    .max(Comparator.comparing(FishingSession::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            int totalStops = plan == null ? 0 : waypointCounts.getOrDefault(plan.getId(), 0L).intValue();
            int visitedStops = 0;
            if (latest != null) {
                List<SessionWaypointProgress> progress = progressBySession.getOrDefault(latest.getId(), List.of());
                totalStops = progress.isEmpty() ? totalStops : progress.size();
                visitedStops = (int) progress.stream().filter(row -> VISITED.contains(row.getStatus())).count();
            }
            cards.add(new PastTripResponse(
                    trip.getId(),
                    plan == null ? null : plan.getId(),
                    trip.getLakeId(),
                    lake == null ? "Lake" : lake.getName(),
                    images.getOrDefault(trip.getLakeId(), cardImageResolver.urlFor(lake)),
                    trip.getPrimaryTargetSpecies(),
                    window.startAt(),
                    window.endAt(),
                    latest == null ? null : latest.getStatus(),
                    completed == null ? null : completed.getId(),
                    completed != null,
                    visitedStops,
                    totalStops
            ));
        }
        cards.sort(Comparator.comparing(PastTripResponse::plannedEndAt).reversed());
        return cards;
    }

    private Map<UUID, TripPlan> latestPlans(List<UUID> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TripPlan> latest = new HashMap<>();
        for (TripPlan plan : tripPlanRepository.findByTripIdInAndStatusIn(tripIds, VISIBLE_PLANS)) {
            TripPlan existing = latest.get(plan.getTripId());
            if (existing == null || plan.getVersion() > existing.getVersion()) {
                latest.put(plan.getTripId(), plan);
            }
        }
        return latest;
    }

    private Map<UUID, Long> waypointCounts(List<UUID> planIds) {
        if (planIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : tripWaypointRepository.countByTripPlanIdIn(planIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Map<UUID, List<SessionWaypointProgress>> progress(List<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        return progressRepository.findByFishingSessionIdIn(sessionIds).stream()
                .collect(Collectors.groupingBy(SessionWaypointProgress::getFishingSessionId));
    }
}
