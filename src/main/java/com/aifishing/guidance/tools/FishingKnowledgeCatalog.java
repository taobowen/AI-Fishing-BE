package com.aifishing.guidance.tools;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Short structured knowledge from existing strategy/tactics vocabulary only.
 * Does not invent free-web facts.
 */
final class FishingKnowledgeCatalog {

    private static final List<Entry> ENTRIES = List.of(
            entry(FeatureType.HUMP.name(), "FEATURE_TYPE", "Raised offshore structure.", "hump", "high spot"),
            entry(FeatureType.DROP_OFF.name(), "FEATURE_TYPE", "Depth break along a contour.", "drop off", "break", "contour"),
            entry(FeatureType.FLAT.name(), "FEATURE_TYPE", "Low-relief area.", "flat"),
            entry(FeatureType.POINT.name(), "FEATURE_TYPE", "Tapering shoreline or underwater point.", "point"),
            entry(FeatureType.BASIN.name(), "FEATURE_TYPE", "Deeper bowl.", "basin"),
            entry(FeatureType.ISLAND_EDGE.name(), "FEATURE_TYPE", "Island shoreline edge.", "island"),
            entry(TechniqueType.NED_RIG.name(), "TECHNIQUE", "Bottom contact with a compact bait.", "ned"),
            entry(TechniqueType.DROP_SHOT.name(), "TECHNIQUE", "Bait suspended above a weight.", "drop shot"),
            entry(TechniqueType.JERKBAIT.name(), "TECHNIQUE", "Twitch-pause hard bait.", "jerkbait"),
            entry(TechniqueType.JIG.name(), "TECHNIQUE", "Hop or drag a jig along structure.", "jig"),
            entry(TechniqueType.TUBE.name(), "TECHNIQUE", "Soft tube worked on or near bottom.", "tube"),
            entry(TechniqueType.SWIMBAIT.name(), "TECHNIQUE", "Swimming bait near cover or contour.", "swimbait", "paddletail"),
            entry(TechniqueType.CRANKBAIT.name(), "TECHNIQUE", "Diving bait with stop-and-go.", "crankbait"),
            entry(TechniqueType.SPINNERBAIT.name(), "TECHNIQUE", "Steady retrieve around cover.", "spinnerbait"),
            entry(TechniqueType.TOPWATER.name(), "TECHNIQUE", "Surface presentation.", "topwater"),
            entry(TechniqueType.TEXAS_RIG.name(), "TECHNIQUE", "Weedless soft plastic on a bullet weight.", "texas"),
            entry(TechniqueType.TROLLING.name(), "TECHNIQUE", "Moving bait behind the boat.", "trolling"),
            entry(TechniqueType.LIVE_BAIT.name(), "TECHNIQUE", "Natural bait presentation.", "live bait"),
            lure(LureFamily.NED_RIG, PresentationTechnique.DRAG_AND_SHAKE,
                    "Drag it a few inches, pause, and shake in place."),
            lure(LureFamily.DROP_SHOT_BAIT, PresentationTechnique.LIFT_DROP,
                    "Lift the bait and let it settle. Watch the slack for a tick."),
            lure(LureFamily.JIG, PresentationTechnique.HOP_ALONG_BOTTOM,
                    "Hop it along the bottom and kill it on the pause."),
            lure(LureFamily.TUBE, PresentationTechnique.DRAG_AND_SHAKE,
                    "Drag it a few inches, pause, and shake in place."),
            lure(LureFamily.PADDLETAIL, PresentationTechnique.SWIM_NEAR_BOTTOM,
                    "Swim it just above the cover and pause if you feel grass."),
            lure(LureFamily.MINNOW_SOFT_PLASTIC, PresentationTechnique.SWIM_NEAR_BOTTOM,
                    "Swim it just above the cover and pause if you feel grass."),
            lure(LureFamily.JERKBAIT, PresentationTechnique.TWITCH_PAUSE,
                    "Hard twitches with long pauses. Most bites come on the stall."),
            lure(LureFamily.CRANKBAIT, PresentationTechnique.STOP_AND_GO,
                    "Crank, pause, and let it float or suspend."),
            lure(LureFamily.LIPLESS_CRANKBAIT, PresentationTechnique.STOP_AND_GO,
                    "Crank, pause, and let it float or suspend."),
            lure(LureFamily.SPINNERBAIT, PresentationTechnique.STEADY_RETRIEVE,
                    "Work it through the structure at a natural pace."),
            lure(LureFamily.CHATTERBAIT, PresentationTechnique.STEADY_RETRIEVE,
                    "Work it through the structure at a natural pace."),
            lure(LureFamily.TOPWATER, PresentationTechnique.WALK_THE_DOG,
                    "Walk it over the cover. Pause beside any opening."),
            lure(LureFamily.FROG, PresentationTechnique.WALK_THE_DOG,
                    "Walk it over the cover. Pause beside any opening."),
            lure(LureFamily.BUZZBAIT, PresentationTechnique.BUZZ,
                    "Keep it buzzing just on the surface through the zone."),
            lure(LureFamily.SPOON, PresentationTechnique.YO_YO,
                    "Rip it up and let it flutter back on a semi-slack line."),
            lure(LureFamily.TEXAS_RIG, PresentationTechnique.HOP_ALONG_BOTTOM,
                    "Hop it along the bottom and kill it on the pause."),
            lure(LureFamily.CAROLINA_RIG, PresentationTechnique.HOP_ALONG_BOTTOM,
                    "Hop it along the bottom and kill it on the pause."),
            presentation(PresentationTechnique.CAST_ACROSS_CONTOUR,
                    "Cast across the contour and work down the break."),
            presentation(PresentationTechnique.DEAD_STICK, "Hold the bait still and wait."),
            presentation(PresentationTechnique.SLOW_ROLL, "Slow, steady retrieve just above cover."),
            presentation(PresentationTechnique.BURN, "Fast retrieve through the zone."),
            presentation(PresentationTechnique.VERTICAL_JIG, "Work the bait up and down under the boat."),
            presentation(PresentationTechnique.POP_AND_PAUSE, "Pop the bait and pause.")
    );

    private FishingKnowledgeCatalog() {
    }

    static List<Entry> lookup(String query, FishSpecies targetSpecies) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = normalize(query);
        Set<String> tokens = tokens(normalized);
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (matches(entry, normalized, tokens)) {
                matches.add(entry);
            }
        }
        if (matches.isEmpty()) {
            return List.of();
        }
        if (targetSpecies != null) {
            matches.add(new Entry(
                    targetSpecies.name(),
                    "SPECIES",
                    "Requested target species from the closed FishSpecies vocabulary.",
                    List.of()
            ));
        }
        return List.copyOf(matches);
    }

    private static boolean matches(Entry entry, String normalized, Set<String> tokens) {
        if (normalized.contains(normalize(entry.term())) || tokens.contains(normalize(entry.term()))) {
            return true;
        }
        for (String alias : entry.aliases()) {
            String aliasNorm = normalize(alias);
            if (normalized.contains(aliasNorm)) {
                return true;
            }
            if (tokens.containsAll(tokens(aliasNorm))) {
                return true;
            }
        }
        return false;
    }

    private static Entry entry(String term, String kind, String summary, String... aliases) {
        return new Entry(term, kind, summary, List.of(aliases));
    }

    private static Entry lure(LureFamily family, PresentationTechnique presentation, String summary) {
        return new Entry(
                family.name(),
                "LURE_FAMILY",
                family.displayName() + ": " + presentation.displayName() + ". " + summary,
                List.of(family.displayName(), presentation.name())
        );
    }

    private static Entry presentation(PresentationTechnique presentation, String summary) {
        return new Entry(
                presentation.name(),
                "PRESENTATION",
                presentation.displayName() + ". " + summary,
                List.of(presentation.displayName())
        );
    }

    private static String normalize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim();
    }

    private static Set<String> tokens(String normalized) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String part : normalized.split("[^a-z0-9]+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    record Entry(String term, String kind, String summary, List<String> aliases) {
        Entry {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
