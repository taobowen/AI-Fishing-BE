package com.aifishing.planning.dto;

import com.aifishing.common.enums.PlanningMode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Balance summary on a generated plan.
 * {@code userFishingMinutes} / {@code aiFishingMinutes} are planned dwell only
 * ({@code planned_dwell_minutes}); transit and wait are excluded.
 * Required-point dwell is included in {@code userFishingMinutes} but is excluded
 * from the Hybrid Template/AI ratio (ratio logic is applied by the planner agent).
 * User stops are REQUIRED + TEMPLATE; AI stops are AI.
 */
@Schema(description = """
        Planning balance for the generated route. userFishingMinutes and aiFishingMinutes \
        are planned fishing dwell only (excluding transit and wait). Required-point dwell \
        counts in userFishingMinutes but is excluded from the Hybrid Template/AI ratio.""")
public record PlanningBalanceResponse(
        PlanningMode mode,
        Integer requiredPointCount,
        Integer templateTargetCount,
        Integer finalUserStopCount,
        Integer finalAiStopCount,
        Integer userFishingMinutes,
        Integer aiFishingMinutes
) {
}
