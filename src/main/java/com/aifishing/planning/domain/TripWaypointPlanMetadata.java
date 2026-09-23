package com.aifishing.planning.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persist writes these exact metadata keys on {@link TripWaypoint}. Parse them
 * here so guidance can read package members before/after that persist lands.
 */
public final class TripWaypointPlanMetadata {

    public static final String MACRO_VISIT_KIND = "macroVisitKind";
    public static final String PACKAGE_MEMBER_IDS = "packageMemberIds";

    private TripWaypointPlanMetadata() {
    }

    public static String macroVisitKind(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(MACRO_VISIT_KIND);
        if (value == null) {
            return null;
        }
        String kind = value.toString().trim();
        return kind.isEmpty() ? null : kind;
    }

    public static List<UUID> packageMemberIds(TripWaypoint waypoint) {
        return waypoint == null ? List.of() : packageMemberIds(waypoint.getMetadata());
    }

    public static List<UUID> packageMemberIds(Map<String, Object> metadata) {
        if (metadata == null) {
            return List.of();
        }
        return parseUuidList(metadata.get(PACKAGE_MEMBER_IDS));
    }

    private static List<UUID> parseUuidList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof UUID uuid) {
            return List.of(uuid);
        }
        if (raw instanceof String text) {
            UUID parsed = parseUuid(text);
            return parsed == null ? List.of() : List.of(parsed);
        }
        if (raw instanceof Collection<?> values) {
            List<UUID> ids = new ArrayList<>();
            for (Object value : values) {
                UUID parsed = parseUuid(value);
                if (parsed != null) {
                    ids.add(parsed);
                }
            }
            return List.copyOf(ids);
        }
        if (raw.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            List<UUID> ids = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                UUID parsed = parseUuid(java.lang.reflect.Array.get(raw, i));
                if (parsed != null) {
                    ids.add(parsed);
                }
            }
            return List.copyOf(ids);
        }
        return List.of();
    }

    private static UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
