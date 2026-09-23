package com.aifishing.planning.ranking;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.LightPreference;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import com.aifishing.strategy.domain.TechniquePreference;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * Time-dependent strategy/depth/window match from the authoritative profile at instant t.
 * Beam identity stays the physical target; utility may differ at 08:00 vs 16:00.
 */
@Component
public class ArrivalStrategyEvaluator {

    private final StrategyWeightResolver weightResolver;

    public ArrivalStrategyEvaluator() {
        this(new StrategyWeightResolver());
    }

    public ArrivalStrategyEvaluator(StrategyWeightResolver weightResolver) {
        this.weightResolver = weightResolver == null ? new StrategyWeightResolver() : weightResolver;
    }

    public ArrivalStrategy evaluate(CandidateSpot spot, Instant instant, PlanningContext context) {
        if (spot == null) {
            return ArrivalStrategy.stored(null, null);
        }
        FishingStrategyProfile profile = context == null ? null : context.profile();
        if (profile == null || instant == null || context.lake() == null) {
            return ArrivalStrategy.stored(spot, null);
        }
        LocalTime local = TripClock.localTime(instant, context.lake());
        StrategyTimeWindow matching = matchingWindow(profile, local);
        List<StructurePreference> effective = weightResolver.effectiveStructures(matching, profile);
        double strategyMatch = spot.getType() == null
                ? clamp(spot.getStrategyWeight())
                : clamp(weightResolver.weightFor(effective, spot.getType()));
        DepthRange depth = matching == null ? null : matching.preferredDepthM();
        double depthTolerance = context.properties() == null
                ? 1.5
                : context.properties().getCandidates().getFallbackDepthToleranceM();
        double depthMatch = SpotRankingService.depthMatch(spot, depth, depthTolerance);
        double timeWindowMatch = matching == null ? 0.4 : 1.0;
        LightPreference light = matching != null
                ? matching.lightPreference()
                : spot.getLightPreference();
        List<TechniquePreference> techniques = weightResolver.effectiveTechniques(matching, profile);
        return new ArrivalStrategy(strategyMatch, depthMatch, timeWindowMatch, depth, light, techniques, matching != null);
    }

    public static StrategyTimeWindow matchingWindow(FishingStrategyProfile profile, LocalTime local) {
        if (profile == null || local == null || profile.timeWindows() == null) {
            return null;
        }
        for (StrategyTimeWindow window : profile.timeWindows()) {
            if (window == null || window.from() == null || window.to() == null) {
                continue;
            }
            if (!local.isBefore(window.from()) && local.isBefore(window.to())) {
                return window;
            }
        }
        return null;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }

    public record ArrivalStrategy(
            double strategyMatch,
            double depthMatch,
            double timeWindowMatch,
            DepthRange depthRange,
            LightPreference lightPreference,
            List<TechniquePreference> techniques,
            boolean inStrategyWindow
    ) {
        public ArrivalStrategy {
            techniques = techniques == null ? List.of() : List.copyOf(techniques);
            lightPreference = lightPreference == null ? LightPreference.NEUTRAL : lightPreference;
        }

        static ArrivalStrategy stored(CandidateSpot spot, DepthRange depthRange) {
            if (spot == null) {
                return new ArrivalStrategy(0, 0.5, 0.4, depthRange, LightPreference.NEUTRAL, List.of(), false);
            }
            return new ArrivalStrategy(
                    clamp(spot.getStrategyWeight()),
                    SpotRankingService.depthMatch(spot, depthRange, 1.5),
                    spot.isWindowSpecific() ? 1.0 : 0.55,
                    depthRange,
                    spot.getLightPreference(),
                    spot.getTechniques(),
                    spot.getWindowFrom() != null
            );
        }
    }
}
