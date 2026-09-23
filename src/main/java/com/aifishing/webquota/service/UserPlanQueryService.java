package com.aifishing.webquota.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.lake.service.LakeCardImageResolver;
import com.aifishing.planning.domain.PlanningRun;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.repo.PlanningRunRepository;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import com.aifishing.webquota.dto.UserPlanSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserPlanQueryService {

    private static final Set<TripPlanStatus> VISIBLE = EnumSet.of(TripPlanStatus.GENERATED, TripPlanStatus.ACCEPTED);

    private final CurrentUser currentUser;
    private final TripRepository tripRepository;
    private final TripPlanRepository tripPlanRepository;
    private final PlanningRunRepository planningRunRepository;
    private final LakeRepository lakeRepository;
    private final LakeCardImageResolver cardImageResolver;

    public UserPlanQueryService(
            CurrentUser currentUser,
            TripRepository tripRepository,
            TripPlanRepository tripPlanRepository,
            PlanningRunRepository planningRunRepository,
            LakeRepository lakeRepository,
            LakeCardImageResolver cardImageResolver
    ) {
        this.currentUser = currentUser;
        this.tripRepository = tripRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.planningRunRepository = planningRunRepository;
        this.lakeRepository = lakeRepository;
        this.cardImageResolver = cardImageResolver;
    }

    @Transactional(readOnly = true)
    public List<UserPlanSummaryResponse> listMine() {
        List<Trip> trips = tripRepository.findOwned(currentUser.id(), null, null, null);
        Map<UUID, Lake> lakes = lakeRepository.findAllById(
                trips.stream().map(Trip::getLakeId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Lake::getId, lake -> lake));
        Map<UUID, String> images = cardImageResolver.urlsFor(lakes.values());
        List<UserPlanSummaryResponse> cards = new ArrayList<>();
        for (Trip trip : trips) {
            tripPlanRepository.findFirstByTripIdAndStatusInOrderByVersionDesc(trip.getId(), VISIBLE)
                    .ifPresent(plan -> cards.add(toCard(trip, plan, lakes.get(trip.getLakeId()), images.get(trip.getLakeId()))));
        }
        cards.sort(Comparator.comparing(UserPlanSummaryResponse::generatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return cards;
    }

    private UserPlanSummaryResponse toCard(Trip trip, TripPlan plan, Lake lake, String lakeCardImageUrl) {
        String lakeName = lake == null ? "Lake" : lake.getName();
        var window = com.aifishing.planning.environment.TripClock.resolve(trip, lake);
        var channel = plan.getPlanningRunId() == null
                ? null
                : planningRunRepository.findById(plan.getPlanningRunId()).map(PlanningRun::getClientChannel).orElse(null);
        return new UserPlanSummaryResponse(
                trip.getId(),
                plan.getId(),
                trip.getLakeId(),
                lakeName,
                lakeCardImageUrl == null ? cardImageResolver.urlFor(lake) : lakeCardImageUrl,
                trip.getPlannedDate(),
                window.plannedEndDate(),
                trip.getPrimaryTargetSpecies(),
                trip.getFishingStartTime(),
                trip.getFishingEndTime(),
                window.startAt(),
                window.endAt(),
                trip.getFishingMode(),
                channel,
                plan.getGeneratedAt(),
                plan.getStatus()
        );
    }
}
