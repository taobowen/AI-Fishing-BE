package com.aifishing.guidance.horizon;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.PlanCreatedBy;
import com.aifishing.guidance.contracts.ReplanScope;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.persistence.GuidancePlanStepEntity;
import com.aifishing.guidance.persistence.GuidancePlanStepRepository;
import com.aifishing.guidance.persistence.GuidancePlanVersionEntity;
import com.aifishing.guidance.persistence.GuidancePlanVersionRepository;
import com.aifishing.guidance.runtime.GuidanceFallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Writes short-horizon versions after a delivered decision. Never mutates
 * {@code trip_plans}. BLOCK prescribed decisions are {@code created_by=SAFETY}.
 */
@Component
public class GuidanceHorizonWriter {

    private final GuidancePlanVersionRepository versionRepository;
    private final GuidancePlanStepRepository stepRepository;

    public GuidanceHorizonWriter(
            GuidancePlanVersionRepository versionRepository,
            GuidancePlanStepRepository stepRepository
    ) {
        this.versionRepository = versionRepository;
        this.stepRepository = stepRepository;
    }

    @Transactional
    public void writeAfterDelivered(UUID fishingSessionId, AgentRunResult result) {
        if (fishingSessionId == null || result == null || result.delivered() == null) {
            return;
        }
        writeAfterDelivered(fishingSessionId, result.delivered());
    }

    @Transactional
    public void writeAfterDelivered(UUID fishingSessionId, DeliveredDecision delivered) {
        if (fishingSessionId == null || delivered == null || GuidanceFallback.isKillSwitch(delivered)) {
            return;
        }
        List<HorizonStep> horizon = CastingOpportunityHorizon.collapseSameStopDwell(delivered.proposedHorizon());
        if (horizon.isEmpty()) {
            return;
        }
        int next = versionRepository.findFirstByFishingSessionIdOrderByVersionDesc(fishingSessionId)
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);
        Integer parent = next == 1 ? null : next - 1;
        boolean safety = GuidanceFallback.SAFETY_BLOCK.equals(delivered.fallbackReason());
        GuidancePlanVersionEntity version = new GuidancePlanVersionEntity();
        version.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        version.setFishingSessionId(fishingSessionId);
        version.setVersion(next);
        version.setParentVersion(parent);
        version.setReplanReason(safety ? GuidanceFallback.SAFETY_BLOCK : delivered.fallbackReason());
        version.setReplanScope(safety ? ReplanScope.SAFETY_OVERRIDE : ReplanScope.TACTICAL_LOCAL);
        version.setCreatedBy(safety ? PlanCreatedBy.SAFETY : PlanCreatedBy.AGENT);
        GuidancePlanVersionEntity saved = versionRepository.save(version);

        List<GuidancePlanStepEntity> steps = new ArrayList<>();
        int index = 1;
        for (HorizonStep step : horizon) {
            GuidancePlanStepEntity row = new GuidancePlanStepEntity();
            row.setSchemaVersion(GuidanceSchemaVersion.VALUE);
            row.setGuidancePlanVersionId(saved.getId());
            row.setStep(step.step() > 0 ? step.step() : index);
            row.setType(step.type());
            row.setCommitted(step.committed());
            row.setTripWaypointId(step.tripWaypointId());
            row.setDurationMinutes(step.durationMinutes());
            steps.add(row);
            index++;
        }
        stepRepository.saveAll(steps);
    }
}
