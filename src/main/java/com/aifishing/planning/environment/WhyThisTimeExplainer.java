package com.aifishing.planning.environment;

import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.service.PlanningContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WhyThisTimeExplainer {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private WhyThisTimeExplainer() {
    }

    public static Result explain(
            RankedCandidate candidate,
            Instant chosen,
            int dwellMinutes,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation,
            TimeAdjustedSpotUtility utility
    ) {
        PlanningProperties.Schedule schedule = context.properties().getSchedule();
        int slot = Math.max(1, schedule.getSlotMinutes());
        int horizon = Math.max(1, schedule.getWhyThisTimeHorizonSlots());
        Instant start = TripClock.startAt(context);
        Instant end = TripClock.endAt(context);
        double chosenValue = utility.dwellValue(candidate, chosen, dwellMinutes, context, weather, orientation);
        Instant bestOtherAt = null;
        double bestOtherValue = Double.NEGATIVE_INFINITY;
        Instant worstEarlierAt = null;
        double worstEarlierValue = Double.POSITIVE_INFINITY;
        int compared = 0;
        for (int i = -horizon; i <= horizon; i++) {
            if (i == 0) {
                continue;
            }
            Instant alt = chosen.plusSeconds((long) i * slot * 60L);
            if (alt.isBefore(start) || alt.isAfter(end.minusSeconds((long) dwellMinutes * 60L))) {
                continue;
            }
            compared++;
            double value = utility.dwellValue(candidate, alt, dwellMinutes, context, weather, orientation);
            if (value > bestOtherValue) {
                bestOtherValue = value;
                bestOtherAt = alt;
            }
            if (alt.isBefore(chosen) && value < worstEarlierValue) {
                worstEarlierValue = value;
                worstEarlierAt = alt;
            }
        }
        ZoneId zone = TripClock.zoneId(context);
        double minDelta = schedule.getWhyThisTimeMinDelta();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("compared", compared > 0);
        evidence.put("chosenUtility", chosenValue);
        evidence.put("slotsCompared", compared);
        if (compared == 0) {
            return Result.nonComparative(chosenValue, nonComparative(utility, candidate, chosen, context, weather, orientation), evidence);
        }
        evidence.put("bestOtherUtility", bestOtherValue);
        evidence.put("deltaVsBestOther", chosenValue - bestOtherValue);
        if (bestOtherAt != null) {
            evidence.put("bestOtherAt", bestOtherAt.toString());
        }
        if (worstEarlierAt != null && chosenValue - worstEarlierValue >= minDelta) {
            String clock = CLOCK.format(chosen.atZone(zone));
            return Result.comparative(chosenValue, worstEarlierValue, chosenValue - worstEarlierValue, worstEarlierAt,
                    "Better after " + clock + ".", evidence);
        }
        if (bestOtherAt != null && bestOtherValue - chosenValue >= minDelta) {
            String clock = CLOCK.format(bestOtherAt.atZone(zone));
            String text = bestOtherAt.isBefore(chosen)
                    ? "This spot is better earlier, around " + clock + "."
                    : "Conditions improve later, around " + clock + ".";
            return Result.comparative(chosenValue, bestOtherValue, bestOtherValue - chosenValue, bestOtherAt, text, evidence);
        }
        return Result.nonComparative(chosenValue, nonComparative(utility, candidate, chosen, context, weather, orientation), evidence);
    }

    private static String nonComparative(
            TimeAdjustedSpotUtility utility,
            RankedCandidate candidate,
            Instant chosen,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation
    ) {
        TimeAdjustedSpotUtility.Evaluation evaluation = utility.evaluate(candidate, chosen, context, weather, orientation, 0);
        if (evaluation.solarInfluence() != null && evaluation.solarInfluence().strength() >= 0.45) {
            return "Favorable light conditions during this window.";
        }
        if (evaluation.windOrientation() == WindOrientation.LEEWARD
                || evaluation.windOrientation() == WindOrientation.PROTECTED) {
            return "Leeward orientation relative to the forecast wind during this window.";
        }
        return "Favorable conditions during this window.";
    }

    public record Result(
            boolean comparative,
            double chosenUtility,
            Double alternativeUtility,
            Double delta,
            Instant alternativeAt,
            List<String> lines,
            Map<String, Object> evidence
    ) {
        public static Result comparative(
                double chosen,
                double alt,
                double delta,
                Instant altAt,
                String text,
                Map<String, Object> evidence
        ) {
            evidence.put("comparative", true);
            return new Result(true, chosen, alt, delta, altAt, List.of(text), evidence);
        }

        public static Result nonComparative(double chosen, String text, Map<String, Object> evidence) {
            evidence.put("comparative", false);
            return new Result(false, chosen, null, null, null, List.of(text), evidence);
        }
    }
}
