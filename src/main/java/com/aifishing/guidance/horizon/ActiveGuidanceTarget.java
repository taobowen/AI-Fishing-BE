package com.aifishing.guidance.horizon;

import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.persistence.GuidancePlanStepEntity;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Latest committed guidance MOVE target. Null when the committed step is STAY,
 * missing, or degraded (zero/multiple committed steps, MOVE without a waypoint).
 */
@Component
public class ActiveGuidanceTarget {

    private final GuidancePlanVersionRepository versionRepository;
    private final GuidancePlanStepRepository stepRepository;

    public ActiveGuidanceTarget(
            GuidancePlanVersionRepository versionRepository,
            GuidancePlanStepRepository stepRepository
    ) {
        this.versionRepository = versionRepository;
        this.stepRepository = stepRepository;
    }

    public UUID resolve(FishingSession session) {
        return session == null ? null : resolve(session.getId());
    }

    public UUID resolve(UUID fishingSessionId) {
        if (fishingSessionId == null || versionRepository == null || stepRepository == null) {
            return null;
        }
        GuidancePlanVersionEntity version = versionRepository
                .findFirstByFishingSessionIdOrderByVersionDesc(fishingSessionId)
                .orElse(null);
        if (version == null) {
            return null;
        }
        return fromEntities(stepRepository.findByGuidancePlanVersionIdOrderByStepAsc(version.getId()));
    }

    public static UUID fromHorizon(List<HorizonStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        List<HorizonStep> committed = new ArrayList<>();
        for (HorizonStep step : steps) {
            if (step != null && step.committed()) {
                committed.add(step);
            }
        }
        if (committed.size() != 1) {
            return null;
        }
        HorizonStep next = committed.getFirst();
        if (next.type() != GuidanceAction.MOVE) {
            return null;
        }
        return next.tripWaypointId();
    }

    public static UUID fromEntities(List<GuidancePlanStepEntity> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        List<GuidancePlanStepEntity> committed = new ArrayList<>();
        for (GuidancePlanStepEntity step : steps) {
            if (step != null && step.isCommitted()) {
                committed.add(step);
            }
        }
        if (committed.size() != 1) {
            return null;
        }
        GuidancePlanStepEntity next = committed.getFirst();
        if (next.getType() != GuidanceAction.MOVE) {
            return null;
        }
        return next.getTripWaypointId();
    }
}
