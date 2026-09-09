package com.aifishing.webquota.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.lake.repo.LakeRepository;
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
import java.util.Set;

@Service
public class UserPlanQueryService {

    private static final Set<TripPlanStatus> VISIBLE = EnumSet.of(TripPlanStatus.GENERATED, TripPlanStatus.ACCEPTED);

    private final CurrentUser currentUser;
    private final TripRepository tripRepository;
    private final TripPlanRepository tripPlanRepository;
    private final PlanningRunRepository planningRunRepository;
    private final LakeRepository lakeRepository;

    public UserPlanQueryService(
            CurrentUser currentUser,
            TripRepository tripRepository,
            TripPlanRepository tripPlanRepository,
            PlanningRunRepository planningRunRepository,
            LakeRepository lakeRepository
    ) {
        this.currentUser = currentUser;
        this.tripRepository = tripRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.planningRunRepository = planningRunRepository;
        this.lakeRepository = lakeRepository;
    }

    @Transactional(readOnly = true)
    public List<UserPlanSummaryResponse> listMine() {
        List<Trip> trips = tripRepository.findOwned(currentUser.id(), null, null, null);
        List<UserPlanSummaryResponse> cards = new ArrayList<>();
        for (Trip trip : trips) {
            tripPlanRepository.findFirstByTripIdAndStatusInOrderByVersionDesc(trip.getId(), VISIBLE)
                    .ifPresent(plan -> cards.add(toCard(trip, plan)));
        }
        cards.sort(Comparator.comparing(UserPlanSummaryResponse::generatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return cards;
    }

    private UserPlanSummaryResponse toCard(Trip trip, TripPlan plan) {
        String lakeName = lakeRepository.findById(trip.getLakeId()).map(lake -> lake.getName()).orElse("Lake");
        var channel = plan.getPlanningRunId() == null
                ? null
                : planningRunRepository.findById(plan.getPlanningRunId()).map(PlanningRun::getClientChannel).orElse(null);
        return new UserPlanSummaryResponse(
                trip.getId(),
                plan.getId(),
                trip.getLakeId(),
                lakeName,
                trip.getPlannedDate(),
                trip.getPrimaryTargetSpecies(),
                trip.getFishingStartTime(),
                trip.getFishingEndTime(),
                trip.getFishingMode(),
                channel,
                plan.getGeneratedAt(),
                plan.getStatus()
        );
    }
}
