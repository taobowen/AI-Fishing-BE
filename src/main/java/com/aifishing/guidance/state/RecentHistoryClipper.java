package com.aifishing.guidance.state;

import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.InferredUserPreference;
import com.aifishing.guidance.contracts.RetrievedMemory;

import java.util.ArrayList;
import java.util.List;

final class RecentHistoryClipper {

    private static final int CLIP_MAX = 240;

    private RecentHistoryClipper() {
    }

    static List<String> clip(
            FishingSessionState state,
            GuidanceTrigger trigger,
            RetrievedMemory memory,
            int limit
    ) {
        int max = Math.max(1, limit);
        List<String> clips = new ArrayList<>();
        FishingSessionState.Recent recent = state == null ? null : state.recent();
        if (recent != null && recent.summaries() != null && !recent.summaries().isEmpty()) {
            recent.summaries().forEach(text -> add(clips, text));
        } else if (recent != null) {
            addCount(clips, recent.moveIds(), "Recent waypoint moves");
            addCount(clips, recent.lureChangeIds(), "Recent lure changes");
            addCount(clips, recent.catchEventIds(), "Recent catches");
            addCount(clips, recent.rejectedAdviceIds(), "Rejected recommendations");
            FishingSessionState.Fishing fishing = state.fishing();
            if (fishing != null && fishing.noBiteMinutes() != null && includeNoBite(trigger)) {
                add(clips, "No bites for " + fishing.noBiteMinutes() + " minutes");
            }
        }
        RetrievedMemory retrieved = memory == null ? RetrievedMemory.empty() : memory;
        if (retrieved.inferredPreferences() != null) {
            for (InferredUserPreference pref : retrieved.inferredPreferences()) {
                add(clips, "Inferred " + pref.key() + "=" + pref.value()
                        + " (evidence " + pref.evidenceCount() + ")");
            }
        }
        if (retrieved.userPreferences() != null) {
            add(clips, explicitClip(retrieved.userPreferences()));
        }
        if (clips.size() <= max) {
            return List.copyOf(clips);
        }
        return List.copyOf(clips.subList(clips.size() - max, clips.size()));
    }

    private static boolean includeNoBite(GuidanceTrigger trigger) {
        return trigger == GuidanceTrigger.NO_BITE_THRESHOLD
                || trigger == GuidanceTrigger.CONSECUTIVE_FAILURE
                || trigger == GuidanceTrigger.USER_REQUEST
                || trigger == GuidanceTrigger.REPEATED_BITE_PATTERN
                || trigger == GuidanceTrigger.USER_STARTED_AD_HOC_FISHING
                || trigger == GuidanceTrigger.USER_ENDED_AD_HOC_FISHING;
    }

    private static void addCount(List<String> clips, List<?> ids, String label) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        add(clips, label + ": " + ids.size());
    }

    private static void add(List<String> clips, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        clips.add(text.length() <= CLIP_MAX ? text : text.substring(0, CLIP_MAX));
    }

    private static String explicitClip(com.aifishing.guidance.contracts.UserFishingPreferences prefs) {
        List<String> parts = new ArrayList<>();
        if (prefs.maxMoveMeters() != null) {
            parts.add("maxMoveMeters=" + prefs.maxMoveMeters());
        }
        if (prefs.avoidLongMoveInWind() != null) {
            parts.add("avoidLongMoveInWind=" + prefs.avoidLongMoveInWind());
        }
        if (prefs.windConservatism() != null) {
            parts.add("windConservatism=" + prefs.windConservatism());
        }
        if (parts.isEmpty()) {
            return "Explicit fishing preferences on file";
        }
        return "Explicit prefs: " + String.join(", ", parts);
    }
}
