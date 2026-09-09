package com.aifishing.planning.tactics;

import com.aifishing.common.enums.LureColorFamily;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.LureLengthBand;
import com.aifishing.common.enums.LureWeightBand;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.strategy.weather.WeatherContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class IdealTacticHeuristic {

    public List<StopTacticalProfile> recommend(List<FishableVisit> visits, PlanningContext context) {
        List<StopTacticalProfile> out = new ArrayList<>();
        for (FishableVisit visit : visits) {
            out.add(profileFor(visit, context));
        }
        return out;
    }

    public StopTacticalProfile profileFor(FishableVisit visit, PlanningContext context) {
        LureFamily idealFamily = idealFamily(visit);
        TacticProfile ideal = tactic(idealFamily, visit, context, true);
        List<TacticProfile> alternatives = new ArrayList<>();
        for (LureFamily family : alternativesFor(idealFamily)) {
            alternatives.add(tactic(family, visit, context, false));
        }
        return new StopTacticalProfile(visit.visitId(), ideal, alternatives);
    }

    private static LureFamily idealFamily(FishableVisit visit) {
        TechniqueType technique = visit.techniques().isEmpty() ? TechniqueType.OTHER : visit.techniques().get(0);
        return switch (technique) {
            case NED_RIG -> LureFamily.NED_RIG;
            case DROP_SHOT -> LureFamily.DROP_SHOT_BAIT;
            case JERKBAIT -> LureFamily.JERKBAIT;
            case JIG -> LureFamily.JIG;
            case TUBE -> LureFamily.TUBE;
            case SWIMBAIT -> LureFamily.PADDLETAIL;
            case CRANKBAIT -> LureFamily.CRANKBAIT;
            case SPINNERBAIT -> LureFamily.SPINNERBAIT;
            case TOPWATER -> visit.representativeDepthM() != null && visit.representativeDepthM() <= 1.2
                    ? LureFamily.FROG
                    : LureFamily.TOPWATER;
            case TEXAS_RIG -> LureFamily.TEXAS_RIG;
            case TROLLING -> LureFamily.CRANKBAIT;
            case LIVE_BAIT, OTHER -> LureFamily.PADDLETAIL;
        };
    }

    private static List<LureFamily> alternativesFor(LureFamily ideal) {
        return switch (ideal) {
            case DROP_SHOT_BAIT -> List.of(LureFamily.NED_RIG, LureFamily.MINNOW_SOFT_PLASTIC);
            case NED_RIG -> List.of(LureFamily.JIG, LureFamily.PADDLETAIL);
            case JIG -> List.of(LureFamily.PADDLETAIL, LureFamily.TUBE);
            case PADDLETAIL -> List.of(LureFamily.MINNOW_SOFT_PLASTIC, LureFamily.JIG);
            case JERKBAIT -> List.of(LureFamily.MINNOW_SOFT_PLASTIC, LureFamily.CRANKBAIT);
            case CRANKBAIT -> List.of(LureFamily.LIPLESS_CRANKBAIT, LureFamily.JERKBAIT);
            case SPINNERBAIT -> List.of(LureFamily.CHATTERBAIT, LureFamily.PADDLETAIL);
            case TOPWATER -> List.of(LureFamily.BUZZBAIT, LureFamily.PADDLETAIL);
            case FROG -> List.of(LureFamily.TOPWATER, LureFamily.BUZZBAIT);
            case TEXAS_RIG -> List.of(LureFamily.JIG, LureFamily.PADDLETAIL);
            case TUBE -> List.of(LureFamily.JIG, LureFamily.NED_RIG);
            case SPOON -> List.of(LureFamily.JIG, LureFamily.PADDLETAIL);
            default -> List.of(LureFamily.PADDLETAIL, LureFamily.JIG);
        };
    }

    private static TacticProfile tactic(
            LureFamily family,
            FishableVisit visit,
            PlanningContext context,
            boolean ideal
    ) {
        LureLengthBand length = family.requiresLength() ? lengthFor(visit) : null;
        LureWeightBand weight = family.requiresWeight() || family.allowsOptionalWeight() ? weightFor(visit) : null;
        if (!family.requiresWeight() && !family.allowsOptionalWeight()) {
            weight = null;
        }
        List<LureColorFamily> colors = colorsFor(context);
        PresentationTechnique presentation = presentationFor(family, visit);
        String pathCue = visit.pathLike()
                ? "Cast across the contour and work down the break."
                : null;
        String instructions = instructionsFor(family, presentation, visit, pathCue);
        String rationale = ideal
                ? "Matches the window technique and structure depth for this stop."
                : "Fishing-valid alternative for the same species, structure, and conditions.";
        return new TacticProfile(
                family,
                length,
                null,
                weight,
                null,
                colors,
                null,
                presentation,
                instructions,
                rationale,
                retrieveSpeed(presentation),
                presentationDepth(visit),
                pathCue
        );
    }

    private static LureLengthBand lengthFor(FishableVisit visit) {
        Double depth = visit.representativeDepthM();
        if (depth != null && depth < 1.5) {
            return LureLengthBand.UNDER_3_IN;
        }
        if (depth != null && depth >= 5) {
            return LureLengthBand.FOUR_TO_5_IN;
        }
        return LureLengthBand.THREE_TO_4_IN;
    }

    private static LureWeightBand weightFor(FishableVisit visit) {
        Double depth = visit.representativeDepthM();
        if (depth != null && depth >= 6) {
            return LureWeightBand.FROM_3_8_TO_1_2;
        }
        if (depth != null && depth >= 3) {
            return LureWeightBand.FROM_1_4_TO_3_8;
        }
        return LureWeightBand.FROM_1_8_TO_1_4;
    }

    private static List<LureColorFamily> colorsFor(PlanningContext context) {
        WeatherContext weather = context == null ? null : context.weather();
        Double clouds = weather == null ? null : weather.cloudCoverPercent();
        if (clouds != null && clouds >= 70) {
            return List.of(LureColorFamily.DARK, LureColorFamily.GREEN_PUMPKIN, LureColorFamily.BLACK);
        }
        if (clouds != null && clouds <= 30) {
            return List.of(LureColorFamily.NATURAL, LureColorFamily.SILVER, LureColorFamily.WHITE_PEARL);
        }
        return List.of(LureColorFamily.NATURAL, LureColorFamily.GREEN_PUMPKIN, LureColorFamily.WHITE_PEARL);
    }

    private static PresentationTechnique presentationFor(LureFamily family, FishableVisit visit) {
        if (visit.pathLike() && (family == LureFamily.PADDLETAIL || family == LureFamily.CRANKBAIT)) {
            return PresentationTechnique.CAST_ACROSS_CONTOUR;
        }
        return switch (family) {
            case DROP_SHOT_BAIT -> PresentationTechnique.LIFT_DROP;
            case NED_RIG, TUBE -> PresentationTechnique.DRAG_AND_SHAKE;
            case JIG -> PresentationTechnique.HOP_ALONG_BOTTOM;
            case PADDLETAIL, MINNOW_SOFT_PLASTIC, GRUB -> PresentationTechnique.SWIM_NEAR_BOTTOM;
            case JERKBAIT -> PresentationTechnique.TWITCH_PAUSE;
            case CRANKBAIT, LIPLESS_CRANKBAIT -> PresentationTechnique.STOP_AND_GO;
            case SPINNERBAIT, CHATTERBAIT, INLINE_SPINNER -> PresentationTechnique.STEADY_RETRIEVE;
            case SPOON -> PresentationTechnique.YO_YO;
            case TOPWATER -> PresentationTechnique.WALK_THE_DOG;
            case FROG -> PresentationTechnique.WALK_THE_DOG;
            case BUZZBAIT -> PresentationTechnique.BUZZ;
            case TEXAS_RIG, CAROLINA_RIG -> PresentationTechnique.HOP_ALONG_BOTTOM;
            case OTHER -> PresentationTechnique.STEADY_RETRIEVE;
        };
    }

    private static String instructionsFor(
            LureFamily family,
            PresentationTechnique presentation,
            FishableVisit visit,
            String pathCue
    ) {
        String base = switch (presentation) {
            case LIFT_DROP -> "Lift the bait and let it settle. Watch the slack for a tick.";
            case DRAG_AND_SHAKE -> "Drag it a few inches, pause, and shake in place.";
            case HOP_ALONG_BOTTOM -> "Hop it along the bottom and kill it on the pause.";
            case SWIM_NEAR_BOTTOM -> "Swim it just above the cover and pause if you feel grass.";
            case TWITCH_PAUSE -> "Hard twitches with long pauses. Most bites come on the stall.";
            case STOP_AND_GO -> "Crank, pause, and let it float or suspend.";
            case CAST_ACROSS_CONTOUR -> "Cast across the contour and work down the break.";
            case WALK_THE_DOG -> "Walk it over the cover. Pause beside any opening.";
            case BUZZ -> "Keep it buzzing just on the surface through the zone.";
            case YO_YO -> "Rip it up and let it flutter back on a semi-slack line.";
            default -> "Work it through the structure at a natural pace.";
        };
        if (pathCue != null && presentation != PresentationTechnique.CAST_ACROSS_CONTOUR) {
            return pathCue + " " + base;
        }
        if (visit.targetKind() == TargetKind.ZONE) {
            return base;
        }
        return family.displayName() + ": " + base;
    }

    private static String retrieveSpeed(PresentationTechnique presentation) {
        return switch (presentation) {
            case BURN, BUZZ -> "fast";
            case STEADY_RETRIEVE, CAST_ACROSS_CONTOUR -> "moderate";
            case DEAD_STICK, LIFT_DROP, DRAG_AND_SHAKE -> "slow";
            default -> "slow to moderate";
        };
    }

    private static String presentationDepth(FishableVisit visit) {
        if (visit.representativeDepthM() == null) {
            return null;
        }
        return String.format(java.util.Locale.ROOT, "%.1fm", visit.representativeDepthM());
    }
}
