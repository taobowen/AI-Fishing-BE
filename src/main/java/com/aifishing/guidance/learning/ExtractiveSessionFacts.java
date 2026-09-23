package com.aifishing.guidance.learning;

import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.persistence.AgentFeedbackEntity;
import com.aifishing.guidance.persistence.LureEventEntity;
import com.aifishing.guidance.persistence.SessionEventEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ExtractiveSessionFacts {

    private ExtractiveSessionFacts() {
    }

    public static String clip(SessionEventEntity event) {
        if (event == null || event.getType() == null) {
            return null;
        }
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        return switch (event.getType()) {
            case WAYPOINT_ENTERED -> "Moved to waypoint " + first(payload, "waypointId", "current");
            case USER_MOVED -> "User moved";
            case LURE_CHANGED -> "Changed lure to " + first(payload, "lureFamily", "unknown");
            case BITE -> "Bite recorded";
            case FISH_ON -> "Fish on";
            case CATCH_CREATED -> "Catch recorded";
            case ADVICE_CREATED -> "Advice delivered";
            case ADVICE_ACCEPTED -> "User accepted advice";
            case ADVICE_REJECTED -> "User rejected advice"
                    + (payload.get("rejectReason") == null ? "" : " (" + payload.get("rejectReason") + ")");
            case NO_BITE -> "No bite window elapsed";
            case SESSION_COMPLETED -> "Session completed";
            default -> null;
        };
    }

    public static List<String> recentClips(List<SessionEventEntity> events, int limit) {
        List<String> clips = new ArrayList<>();
        if (events == null) {
            return List.of();
        }
        for (SessionEventEntity event : events) {
            String clip = clip(event);
            if (clip != null) {
                clips.add(trim(clip, 240));
            }
        }
        if (clips.size() <= limit) {
            return List.copyOf(clips);
        }
        return List.copyOf(clips.subList(clips.size() - limit, clips.size()));
    }

    public static List<String> keyFacts(
            List<SessionEventEntity> events,
            List<CatchEvent> catches,
            List<LureEventEntity> lureEvents,
            List<AgentFeedbackEntity> feedback
    ) {
        List<SessionEventEntity> safeEvents = events == null ? List.of() : events;
        List<CatchEvent> activeCatches = catches == null
                ? List.of()
                : catches.stream().filter(row -> row.getStatus() == CatchStatus.ACTIVE).toList();
        long fishOn = count(safeEvents, SessionEventType.FISH_ON);
        long bites = count(safeEvents, SessionEventType.BITE);
        long rejects = count(safeEvents, SessionEventType.ADVICE_REJECTED);
        long waypoints = count(safeEvents, SessionEventType.WAYPOINT_ENTERED);
        long lureChanges = lureEvents == null ? 0 : lureEvents.size();
        long landed = activeCatches.stream().filter(row -> row.getOutcome() == CatchOutcome.LANDED).count();
        long lost = activeCatches.stream().filter(row -> row.getOutcome() == CatchOutcome.LOST).count();
        List<String> facts = new ArrayList<>();
        facts.add(fishOn + " fish-on");
        facts.add(bites + " bites");
        facts.add(landed + " landed");
        if (lost > 0) {
            facts.add(lost + " lost after hookup");
        }
        facts.add(lureChanges + " lure changes");
        facts.add(waypoints + " waypoint arrivals");
        if (rejects > 0) {
            facts.add(rejects + " rejected recommendations");
        }
        if (feedback != null) {
            feedback.stream()
                    .filter(row -> row.getStatus() == FeedbackStatus.REJECTED && row.getRejectReason() != null)
                    .map(row -> "Rejected: " + row.getRejectReason())
                    .distinct()
                    .forEach(facts::add);
        }
        activeCatches.stream()
                .filter(row -> row.getSpecies() != null)
                .map(row -> (row.getOutcome() == CatchOutcome.LOST ? "Lost " : "Landed ") + row.getSpecies().name())
                .distinct()
                .forEach(facts::add);
        return facts.stream().map(fact -> trim(fact, 240)).toList();
    }

    public static String extractiveSummary(List<String> keyFacts) {
        if (keyFacts == null || keyFacts.isEmpty()) {
            return "Session completed with no recorded fish-on, catch, or preference events.";
        }
        return trim("Session: " + String.join("; ", keyFacts) + ".", 2000);
    }

    private static long count(List<SessionEventEntity> events, SessionEventType type) {
        return events.stream().filter(event -> event.getType() == type).count();
    }

    private static String first(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
