package com.aifishing.guidance.attribution;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.FeedbackStatus;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.RecommendationRole;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.SessionEventType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class UserActionInference {

    private UserActionInference() {
    }

    public static List<ObservedUserAction> infer(
            DeliveredSnapshot snapshot,
            SessionEventType type,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
        if (snapshot == null || snapshot.decision() == null || type == null || occurredAt == null) {
            return List.of();
        }
        Map<String, Object> source = payload == null ? Map.of() : payload;
        return switch (type) {
            case WAYPOINT_ENTERED -> inferArrival(snapshot, occurredAt, source);
            case WAYPOINT_LEFT -> inferLeave(snapshot, occurredAt, source);
            case LURE_CHANGED, DEPTH_CHANGED, RETRIEVE_CHANGED -> inferPresentation(snapshot, occurredAt, source);
            case ADVICE_REJECTED -> inferReject(snapshot, occurredAt, source);
            case ADVICE_CREATED, ADVICE_ACCEPTED -> inferStayIfPresent(snapshot, occurredAt, source);
            case ADVICE_ACKNOWLEDGED -> List.of();
            default -> List.of();
        };
    }

    public static List<ObservedUserAction> inferReject(
            DeliveredSnapshot snapshot,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
        List<ObservedUserAction> actions = new ArrayList<>();
        for (RecommendedSlice slice : AttributionWindows.slices(snapshot)) {
            actions.add(action(snapshot, occurredAt, slice.action(), false, slice.role(), payload));
        }
        return actions;
    }

    public static List<ObservedUserAction> inferStayIfPresent(
            DeliveredSnapshot snapshot,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
        if (!Boolean.TRUE.equals(truth(payload, "onWaypoint"))) {
            return List.of();
        }
        List<ObservedUserAction> actions = new ArrayList<>();
        for (RecommendedSlice slice : AttributionWindows.slices(snapshot)) {
            if (slice.action() == GuidanceAction.STAY) {
                actions.add(action(snapshot, occurredAt, GuidanceAction.STAY, true, slice.role(), payload));
            }
        }
        return actions;
    }

    public static FeedbackStatus feedbackEventType(SessionEventType type) {
        if (type == SessionEventType.ADVICE_ACCEPTED) {
            return FeedbackStatus.ACCEPTED;
        }
        if (type == SessionEventType.ADVICE_REJECTED) {
            return FeedbackStatus.REJECTED;
        }
        if (type == SessionEventType.ADVICE_ACKNOWLEDGED) {
            return FeedbackStatus.ACKNOWLEDGED;
        }
        return null;
    }

    private static List<ObservedUserAction> inferArrival(
            DeliveredSnapshot snapshot,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
        UUID arrived = uuid(payload, "waypointId");
        List<ObservedUserAction> actions = new ArrayList<>();
        for (RecommendedSlice slice : AttributionWindows.slices(snapshot)) {
            if (slice.dimension() != AttributionDimension.LOCATION) {
                continue;
            }
            if (slice.action() == GuidanceAction.MOVE) {
                boolean followed = arrived != null && arrived.equals(snapshot.decision().targetTripWaypointId());
                actions.add(action(snapshot, occurredAt, GuidanceAction.MOVE, followed, slice.role(), payload));
            } else if (slice.action() == GuidanceAction.STAY) {
                actions.add(action(snapshot, occurredAt, GuidanceAction.STAY, true, slice.role(), payload));
            }
        }
        return actions;
    }

    private static List<ObservedUserAction> inferLeave(
            DeliveredSnapshot snapshot,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
        List<ObservedUserAction> actions = new ArrayList<>();
        for (RecommendedSlice slice : AttributionWindows.slices(snapshot)) {
            if (slice.action() == GuidanceAction.STAY) {
                Map<String, Object> body = new HashMap<>(payload);
                body.put("endedStayWindow", true);
                actions.add(action(snapshot, occurredAt, GuidanceAction.STAY, true, slice.role(), body));
            }
        }
        return actions;
    }

    private static List<ObservedUserAction> inferPresentation(
            DeliveredSnapshot snapshot,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
        List<ObservedUserAction> actions = new ArrayList<>();
        LureFamily lure = enumValue(payload, "lureFamily", LureFamily.class);
        RetrieveStyle retrieve = enumValue(payload, "retrieveStyle", RetrieveStyle.class);
        Double depth = decimal(payload, "depthM");
        for (RecommendedSlice slice : AttributionWindows.slices(snapshot)) {
            boolean followed = switch (slice.action()) {
                case CHANGE_LURE -> lure != null && lure == snapshot.decision().suggestedLure();
                case CHANGE_RETRIEVE -> retrieve != null && retrieve == snapshot.decision().retrieveStyle();
                case CHANGE_DEPTH -> depthMatches(depth, snapshot.decision().depthMinM(), snapshot.decision().depthMaxM());
                default -> false;
            };
            if (slice.action() == GuidanceAction.CHANGE_LURE
                    || slice.action() == GuidanceAction.CHANGE_RETRIEVE
                    || slice.action() == GuidanceAction.CHANGE_DEPTH) {
                actions.add(action(snapshot, occurredAt, slice.action(), followed, slice.role(), payload));
            }
        }
        return actions;
    }

    private static ObservedUserAction action(
            DeliveredSnapshot snapshot,
            Instant occurredAt,
            GuidanceAction actual,
            boolean followed,
            RecommendationRole role,
            Map<String, Object> payload
    ) {
        Map<String, Object> body = new HashMap<>(payload == null ? Map.of() : payload);
        return new ObservedUserAction(
                snapshot.deliveredEntityId(),
                occurredAt,
                actual,
                followed,
                role,
                followed && role == RecommendationRole.PRIMARY,
                body
        );
    }

    private static boolean depthMatches(Double depth, Double min, Double max) {
        if (depth == null) {
            return false;
        }
        if (min != null && depth < min) {
            return false;
        }
        return max == null || depth <= max;
    }

    static UUID uuid(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static Boolean truth(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    static Double decimal(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static <E extends Enum<E>> E enumValue(Map<String, Object> payload, String key, Class<E> type) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        try {
            return Enum.valueOf(type, String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static String sourceEventId(Map<String, Object> payload) {
        Object value = payload == null ? null : payload.get("sourceEventId");
        return value == null ? null : String.valueOf(value);
    }

    static boolean sameObservation(ObservedUserAction left, ObservedUserAction right) {
        return left != null
                && right != null
                && Objects.equals(left.deliveredDecisionId(), right.deliveredDecisionId())
                && left.recommendationRole() == right.recommendationRole()
                && left.actualAction() == right.actualAction()
                && Objects.equals(sourceEventId(left.payload()), sourceEventId(right.payload()));
    }
}
