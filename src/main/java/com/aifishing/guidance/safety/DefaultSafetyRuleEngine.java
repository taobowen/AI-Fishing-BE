package com.aifishing.guidance.safety;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.SafetyConstraintCode;
import com.aifishing.guidance.contracts.SafetyVerdict;
import com.aifishing.guidance.contracts.SafetyVerdictLevel;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.guidance.spi.SafetyRuleEngine;
import com.aifishing.planning.PlanningProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * State-only hard safety. Does not receive a candidate. Wind uses
 * {@code app.planning.safety} thresholds; this is the only weather/wind
 * calculation point. Runtime (workstream D) must deliver
 * {@link SafetyVerdict#prescribedAction()} when {@code level=BLOCK}
 * ({@code fallbackUsed=true}); do not choose STAY vs RETURN in code.
 */
@Component
public class DefaultSafetyRuleEngine implements SafetyRuleEngine {

    private static final List<GuidanceAction> ALL_ACTIONS = List.of(GuidanceAction.values());

    private final PlanningProperties planningProperties;
    private final SessionProperties sessionProperties;

    public DefaultSafetyRuleEngine(PlanningProperties planningProperties, SessionProperties sessionProperties) {
        this.planningProperties = planningProperties;
        this.sessionProperties = sessionProperties;
    }

    @Override
    public SafetyVerdict evaluate(FishingSessionState state) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        List<Finding> findings = new ArrayList<>();
        collectWeather(state, findings);
        collectWind(state, findings);
        collectRange(state, findings);
        collectGps(state, findings);

        SafetyVerdictLevel level = SafetyVerdictLevel.OK;
        Set<SafetyConstraintCode> codes = new LinkedHashSet<>();
        List<String> reasons = new ArrayList<>();
        for (Finding finding : findings) {
            if (severity(finding.level) > severity(level)) {
                level = finding.level;
            }
            codes.add(finding.code);
            reasons.add(finding.reason);
        }

        GuidanceAction prescribed = level == SafetyVerdictLevel.BLOCK ? GuidanceAction.RETURN : null;
        return new SafetyVerdict(
                GuidanceSchemaVersion.VALUE,
                level,
                List.copyOf(reasons),
                List.copyOf(codes),
                List.copyOf(allowedActions(level, codes)),
                prescribed
        );
    }

    private void collectWeather(FishingSessionState state, List<Finding> findings) {
        FishingSessionState.Environment environment = state.environment();
        if (environment == null || environment.weather() != WeatherCondition.THUNDERSTORM) {
            return;
        }
        findings.add(new Finding(
                SafetyVerdictLevel.BLOCK,
                SafetyConstraintCode.THUNDERSTORM,
                "Thunderstorm conditions are present on the water"
        ));
        findings.add(new Finding(
                SafetyVerdictLevel.BLOCK,
                SafetyConstraintCode.LIGHTNING,
                "Thunderstorm conditions include lightning risk"
        ));
    }

    private void collectWind(FishingSessionState state, List<Finding> findings) {
        FishingSessionState.Environment environment = state.environment();
        Double windKph = environment == null ? null : environment.windSpeedKph();
        if (windKph == null || windKph.isNaN() || windKph < 0) {
            return;
        }
        PlanningProperties.Safety safety = planningProperties.getSafety();
        double hardReject = safety.getWindHardRejectKmh();
        double penalty = safety.getWindPenaltyKmh();
        if (windKph >= hardReject) {
            findings.add(new Finding(
                    SafetyVerdictLevel.BLOCK,
                    SafetyConstraintCode.WIND_UNSAFE,
                    "Wind speed " + format(windKph) + " km/h meets or exceeds the hard-reject threshold "
                            + format(hardReject) + " km/h"
            ));
            return;
        }
        if (windKph >= penalty) {
            findings.add(new Finding(
                    SafetyVerdictLevel.WARNING,
                    SafetyConstraintCode.WIND_UNSAFE,
                    "Wind speed " + format(windKph) + " km/h meets or exceeds the penalty threshold "
                            + format(penalty) + " km/h"
            ));
        }
    }

    private void collectRange(FishingSessionState state, List<Finding> findings) {
        FishingSessionState.Boat boat = state.boat();
        if (boat == null) {
            return;
        }
        Double remaining = boat.remainingRangeMeters();
        Double reserve = boat.returnReserveMeters();
        if (remaining == null) {
            return;
        }
        if (remaining <= 0) {
            findings.add(new Finding(
                    SafetyVerdictLevel.BLOCK,
                    SafetyConstraintCode.RANGE_INSUFFICIENT,
                    "Remaining range is exhausted"
            ));
        }
        if (reserve != null && remaining <= reserve) {
            if (remaining > 0) {
                findings.add(new Finding(
                        SafetyVerdictLevel.BLOCK,
                        SafetyConstraintCode.RANGE_INSUFFICIENT,
                        "Remaining range " + format(remaining) + " m is at or below the reserve distance "
                                + format(reserve) + " m"
                ));
            }
            findings.add(new Finding(
                    SafetyVerdictLevel.BLOCK,
                    SafetyConstraintCode.RETURN_RESERVE_INSUFFICIENT,
                    "Reserve distance " + format(reserve) + " m is not covered by remaining range "
                            + format(remaining) + " m"
            ));
        }
    }

    private void collectGps(FishingSessionState state, List<Finding> findings) {
        FishingSessionState.Position position = state.position();
        Double accuracyM = position == null ? null : position.gpsAccuracyM();
        if (accuracyM == null || accuracyM.isNaN() || accuracyM < 0) {
            return;
        }
        double maxAccuracyM = sessionProperties.getLocation().getMaxAccuracyM();
        if (accuracyM > maxAccuracyM) {
            findings.add(new Finding(
                    SafetyVerdictLevel.WARNING,
                    SafetyConstraintCode.GPS_ACCURACY_UNACCEPTABLE,
                    "GPS accuracy " + format(accuracyM) + " m exceeds the acceptable maximum "
                            + format(maxAccuracyM) + " m"
            ));
        }
    }

    private static List<GuidanceAction> allowedActions(SafetyVerdictLevel level, Set<SafetyConstraintCode> codes) {
        if (level == SafetyVerdictLevel.BLOCK) {
            return List.of(GuidanceAction.RETURN);
        }
        EnumSet<GuidanceAction> allowed = EnumSet.allOf(GuidanceAction.class);
        if (level == SafetyVerdictLevel.WARNING && (codes.contains(SafetyConstraintCode.WIND_UNSAFE)
                || codes.contains(SafetyConstraintCode.GPS_ACCURACY_UNACCEPTABLE))) {
            allowed.remove(GuidanceAction.MOVE);
        }
        List<GuidanceAction> ordered = new ArrayList<>();
        for (GuidanceAction action : ALL_ACTIONS) {
            if (allowed.contains(action)) {
                ordered.add(action);
            }
        }
        return ordered;
    }

    private static int severity(SafetyVerdictLevel level) {
        return switch (level) {
            case OK -> 0;
            case WARNING -> 1;
            case BLOCK -> 2;
        };
    }

    private static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    private record Finding(SafetyVerdictLevel level, SafetyConstraintCode code, String reason) {
    }
}
