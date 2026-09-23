package com.aifishing.guidance.dispatch;

import com.aifishing.guidance.contracts.GuidanceTrigger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Single-flight merge ranks. Lower rank wins. {@code USER_REQUEST} is never an
 * outbox primary.
 */
public final class TriggerPriority {

    private TriggerPriority() {
    }

    public static int rank(GuidanceTrigger trigger) {
        if (trigger == null) {
            return Integer.MAX_VALUE;
        }
        return switch (trigger) {
            case SAFETY_STATE_CHANGED, RETURN_RISK_CHANGED -> 1;
            case USER_STARTED_AD_HOC_FISHING -> 2;
            case ROUTE_DEVIATION, SIGNIFICANT_LOCATION_CHANGE, SIGNIFICANT_WEATHER_CHANGE, WAYPOINT_REACHED,
                 USER_ENDED_AD_HOC_FISHING -> 3;
            case FISH_ON -> 4;
            case REPEATED_BITE_PATTERN, PLAN_STEP_COMPLETED, CONSECUTIVE_FAILURE -> 5;
            case NO_BITE_THRESHOLD -> 6;
            case USER_REQUEST -> 99;
        };
    }

    public static GuidanceTrigger higher(GuidanceTrigger left, GuidanceTrigger right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (isAdHocStart(left) && isAdHocEnd(right)) {
            return right;
        }
        if (isAdHocEnd(left) && isAdHocStart(right)) {
            return left;
        }
        return rank(left) <= rank(right) ? left : right;
    }

    private static boolean isAdHocStart(GuidanceTrigger trigger) {
        return trigger == GuidanceTrigger.USER_STARTED_AD_HOC_FISHING;
    }

    private static boolean isAdHocEnd(GuidanceTrigger trigger) {
        return trigger == GuidanceTrigger.USER_ENDED_AD_HOC_FISHING;
    }

    public static List<GuidanceTrigger> relatedWithoutPrimary(
            GuidanceTrigger primary,
            List<GuidanceTrigger> first,
            List<GuidanceTrigger> second
    ) {
        Set<GuidanceTrigger> related = new LinkedHashSet<>();
        addAll(related, first);
        addAll(related, second);
        if (primary != null) {
            related.remove(primary);
        }
        related.remove(GuidanceTrigger.USER_REQUEST);
        return List.copyOf(related);
    }

    public static List<String> mergeReasons(List<String> first, List<String> second) {
        Set<String> codes = new LinkedHashSet<>();
        addReasons(codes, first);
        addReasons(codes, second);
        return List.copyOf(codes);
    }

    private static void addAll(Set<GuidanceTrigger> target, List<GuidanceTrigger> source) {
        if (source == null) {
            return;
        }
        for (GuidanceTrigger trigger : source) {
            if (trigger != null && trigger != GuidanceTrigger.USER_REQUEST) {
                target.add(trigger);
            }
        }
    }

    private static void addReasons(Set<String> target, List<String> source) {
        if (source == null) {
            return;
        }
        for (String code : source) {
            if (code != null && !code.isBlank()) {
                target.add(code);
            }
        }
    }

    public static List<GuidanceTrigger> allTriggers(GuidanceTrigger primary, List<GuidanceTrigger> related) {
        List<GuidanceTrigger> all = new ArrayList<>();
        if (primary != null && primary != GuidanceTrigger.USER_REQUEST) {
            all.add(primary);
        }
        if (related != null) {
            for (GuidanceTrigger trigger : related) {
                if (trigger != null && trigger != GuidanceTrigger.USER_REQUEST && !all.contains(trigger)) {
                    all.add(trigger);
                }
            }
        }
        return all;
    }
}
