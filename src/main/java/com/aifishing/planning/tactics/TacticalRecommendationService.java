package com.aifishing.planning.tactics;

import com.aifishing.common.enums.GearType;
import com.aifishing.gear.domain.Gear;
import com.aifishing.gear.repo.GearRepository;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.GenerateProfiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TacticalRecommendationService {

    public static final String ALGORITHM_VERSION = "1.0.0";
    public static final String WARNING_UNAVAILABLE = "TACTICAL_RECOMMENDATIONS_UNAVAILABLE";

    private static final Logger log = LoggerFactory.getLogger(TacticalRecommendationService.class);

    private final GearRepository gearRepository;
    private final IdealTacticHeuristic heuristic;
    private final IdealTacticsAiClient aiClient;

    @Autowired
    public TacticalRecommendationService(
            GearRepository gearRepository,
            IdealTacticHeuristic heuristic,
            IdealTacticsAiClient aiClient
    ) {
        this.gearRepository = gearRepository;
        this.heuristic = heuristic;
        this.aiClient = aiClient;
    }

    TacticalRecommendationService(IdealTacticHeuristic heuristic, IdealTacticsAiClient aiClient) {
        this(null, heuristic, aiClient);
    }

    public TacticalPlan recommend(List<PlannedStop> stops, PlanningContext context) {
        return recommendVisits(TacticalVisits.extract(stops), context);
    }

    public TacticalPlan recommendVisits(List<FishableVisit> visits, PlanningContext context) {
        try {
            if (visits == null || visits.isEmpty()) {
                return TacticalPlan.empty();
            }
            List<StopTacticalProfile> profiles = ideals(visits, context);
            if (profiles == null) {
                return TacticalPlan.unavailable();
            }
            UUID userId = context == null || context.trip() == null ? null : context.trip().getUserId();
            List<LockerLure> locker = loadLocker(userId);
            Map<UUID, TacticalRecommendation> byVisit = new LinkedHashMap<>();
            for (StopTacticalProfile profile : profiles) {
                TacticalRecommendation matched = LureLockerMatcher.match(profile, locker);
                if (matched != null) {
                    byVisit.put(profile.visitId(), matched);
                }
            }
            return new TacticalPlan(byVisit, null);
        } catch (Exception ex) {
            log.warn("Tactical recommendations failed; plan will still persist: {}", ex.getMessage());
            return TacticalPlan.unavailable();
        }
    }

    private List<StopTacticalProfile> ideals(List<FishableVisit> visits, PlanningContext context) {
        if (aiClient.configured()) {
            GenerateProfiler.current().count("tacticsAiCalls");
            GenerateProfiler.current().start(GenerateProfiler.TACTICS_AI);
            try {
                return aiClient.recommend(visits, context);
            } catch (Exception ex) {
                log.warn("Tactical AI failed; using heuristic: {}", ex.getMessage());
            } finally {
                GenerateProfiler.current().end(GenerateProfiler.TACTICS_AI);
            }
        }
        try {
            return heuristic.recommend(visits, context);
        } catch (Exception ex) {
            log.warn("Tactical heuristic failed: {}", ex.getMessage());
            return null;
        }
    }

    private List<LockerLure> loadLocker(UUID userId) {
        if (userId == null || gearRepository == null) {
            return List.of();
        }
        List<LockerLure> locker = new ArrayList<>();
        for (Gear gear : gearRepository.findByUserIdOrderByNameAsc(userId)) {
            if (!gear.isActive() || gear.getType() != GearType.LURE) {
                continue;
            }
            LockerLure lure = LockerLure.from(gear);
            if (lure != null) {
                locker.add(lure);
            }
        }
        return locker;
    }

    public record TacticalPlan(
            Map<UUID, TacticalRecommendation> byVisitId,
            String warning
    ) {
        public TacticalPlan {
            byVisitId = byVisitId == null ? Map.of() : Map.copyOf(byVisitId);
        }

        public static TacticalPlan empty() {
            return new TacticalPlan(Map.of(), null);
        }

        public static TacticalPlan unavailable() {
            return new TacticalPlan(Map.of(), WARNING_UNAVAILABLE);
        }

        public boolean usable() {
            return warning == null && !byVisitId.isEmpty();
        }
    }
}
