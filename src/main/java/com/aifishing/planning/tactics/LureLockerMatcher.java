package com.aifishing.planning.tactics;

import com.aifishing.common.enums.LureColorFamily;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.gear.lure.LureProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LureLockerMatcher {

    private LureLockerMatcher() {
    }

    public static TacticalRecommendation match(StopTacticalProfile profile, List<LockerLure> locker) {
        if (profile == null || profile.visitId() == null || profile.idealTactic() == null) {
            return null;
        }
        List<LockerLure> declared = eligible(profile, locker == null ? List.of() : locker);
        RankedMatch best = declared.stream()
                .map(item -> rank(profile, item))
                .max(Comparator.naturalOrder())
                .orElse(null);
        boolean sufficient = best != null && sufficient(profile.idealTactic(), best.lure().profile());
        TacticProfile selectedTactic = best == null ? null : tacticFor(profile, best.lure().profile().lureFamily());
        LockerTechnique technique = selectedTactic == null ? null : new LockerTechnique(
                selectedTactic.presentationTechnique(),
                selectedTactic.instructions(),
                selectedTactic.rationale()
        );
        return new TacticalRecommendation(
                profile.visitId(),
                profile.idealTactic(),
                profile.acceptableAlternativeTactics(),
                best == null ? null : best.lure().gearItemId(),
                best == null ? null : snapshot(best.lure()),
                technique,
                sufficient,
                !sufficient,
                null
        );
    }

    static List<LockerLure> eligible(StopTacticalProfile profile, List<LockerLure> locker) {
        Set<LureFamily> declared = declaredFamilies(profile);
        List<LockerLure> out = new ArrayList<>();
        for (LockerLure lure : locker) {
            if (lure == null || lure.gearItemId() == null || lure.profile() == null) {
                continue;
            }
            if (declared.contains(lure.profile().lureFamily())) {
                out.add(lure);
            }
        }
        return out;
    }

    static Set<LureFamily> declaredFamilies(StopTacticalProfile profile) {
        Set<LureFamily> families = new HashSet<>();
        if (profile.idealTactic() != null && profile.idealTactic().lureFamily() != null) {
            families.add(profile.idealTactic().lureFamily());
        }
        for (TacticProfile alternative : profile.acceptableAlternativeTactics()) {
            if (alternative != null && alternative.lureFamily() != null) {
                families.add(alternative.lureFamily());
            }
        }
        return families;
    }

    private static RankedMatch rank(StopTacticalProfile profile, LockerLure lure) {
        TacticProfile ideal = profile.idealTactic();
        LureProfile item = lure.profile();
        int familyScore;
        if (item.lureFamily() == ideal.lureFamily()) {
            familyScore = 1_000;
        } else {
            int index = alternativeIndex(profile, item.lureFamily());
            familyScore = index < 0 ? 0 : 500 - index;
        }
        TacticProfile declared = tacticFor(profile, item.lureFamily());
        int sizeScore = sizeCompatible(declared, item) ? 80 : 0;
        int colorScore = colorOverlap(declared, item) ? 15 : 0;
        int weightScore = weightCompatible(declared, item) ? 10 : 0;
        return new RankedMatch(lure, familyScore * 100 + sizeScore * 10 + colorScore + weightScore);
    }

    private static boolean sufficient(TacticProfile ideal, LureProfile item) {
        return item.lureFamily() == ideal.lureFamily() && sizeCompatible(ideal, item);
    }

    static boolean sizeCompatible(TacticProfile tactic, LureProfile item) {
        if (tactic == null || item == null || tactic.lureFamily() == null) {
            return false;
        }
        if (tactic.requiresLength()) {
            return tactic.lengthBand() != null
                    && item.lengthBand() != null
                    && tactic.lengthBand() == item.lengthBand();
        }
        if (tactic.requiresWeight()) {
            return tactic.weightBand() != null
                    && item.weightBand() != null
                    && tactic.weightBand() == item.weightBand();
        }
        return true;
    }

    private static boolean weightCompatible(TacticProfile tactic, LureProfile item) {
        if (tactic == null || item == null) {
            return false;
        }
        if (tactic.weightBand() != null && item.weightBand() != null) {
            return tactic.weightBand() == item.weightBand();
        }
        if (tactic.optionalWeight() != null && item.weightOz() != null) {
            return Math.abs(tactic.optionalWeight() - item.weightOz()) <= 0.15;
        }
        return false;
    }

    private static boolean colorOverlap(TacticProfile tactic, LureProfile item) {
        if (tactic == null || tactic.preferredColors().isEmpty() || item.colors().isEmpty()) {
            return false;
        }
        for (LureColorFamily color : item.colors()) {
            if (tactic.preferredColors().contains(color)) {
                return true;
            }
        }
        return false;
    }

    private static int alternativeIndex(StopTacticalProfile profile, LureFamily family) {
        List<TacticProfile> alternatives = profile.acceptableAlternativeTactics();
        for (int i = 0; i < alternatives.size(); i++) {
            TacticProfile alternative = alternatives.get(i);
            if (alternative != null && alternative.lureFamily() == family) {
                return i;
            }
        }
        return -1;
    }

    private static TacticProfile tacticFor(StopTacticalProfile profile, LureFamily family) {
        if (profile.idealTactic() != null && profile.idealTactic().lureFamily() == family) {
            return profile.idealTactic();
        }
        for (TacticProfile alternative : profile.acceptableAlternativeTactics()) {
            if (alternative != null && alternative.lureFamily() == family) {
                return alternative;
            }
        }
        return null;
    }

    private static LockerSnapshot snapshot(LockerLure lure) {
        LureProfile profile = lure.profile();
        return new LockerSnapshot(
                profile.lureFamily(),
                profile.sizeLabel(),
                profile.colors(),
                profile.weightOz(),
                lure.name() != null ? lure.name() : profile.autoName()
        );
    }

    private record RankedMatch(LockerLure lure, int score) implements Comparable<RankedMatch> {
        @Override
        public int compareTo(RankedMatch other) {
            return Integer.compare(score, other.score);
        }
    }
}
