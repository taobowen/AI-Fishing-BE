package com.aifishing.lake.ingestion.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AccessOwnership {

    public static final String PRIVATE = "PRIVATE";
    public static final String MUNICIPAL = "MUNICIPAL";
    public static final String PROVINCIAL = "PROVINCIAL";
    public static final String FEDERAL = "FEDERAL";
    public static final String PUBLIC = "PUBLIC";
    public static final String UNKNOWN = "UNKNOWN";

    public static final String PRIVATE_LAUNCH_PERMISSION_REQUIRED = "PRIVATE_LAUNCH_PERMISSION_REQUIRED";
    public static final String OWNERSHIP_UNVERIFIED = "OWNERSHIP_UNVERIFIED";

    private static final Set<String> GOVERNMENT = Set.of(MUNICIPAL, PROVINCIAL, FEDERAL, PUBLIC);

    private AccessOwnership() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank() || "unknown".equalsIgnoreCase(raw.trim())) {
            return UNKNOWN;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (token) {
            case PRIVATE -> PRIVATE;
            case MUNICIPAL -> MUNICIPAL;
            case PROVINCIAL -> PROVINCIAL;
            case FEDERAL -> FEDERAL;
            case PUBLIC, "CROWN" -> PUBLIC;
            default -> UNKNOWN;
        };
    }

    public static boolean privateOwned(String ownershipType) {
        return PRIVATE.equals(ownershipType);
    }

    public static boolean governmentOwned(String ownershipType) {
        return GOVERNMENT.contains(ownershipType);
    }

    public static boolean unverified(String ownershipType) {
        return ownershipType == null || UNKNOWN.equals(ownershipType);
    }

    public static boolean autoAllowed(String ownershipType) {
        return !privateOwned(ownershipType);
    }

    public static List<String> launchWarnings(String ownershipType, List<String> existing) {
        List<String> warnings = new ArrayList<>(existing == null ? List.of() : existing);
        if (privateOwned(ownershipType) && !warnings.contains(PRIVATE_LAUNCH_PERMISSION_REQUIRED)) {
            warnings.add(PRIVATE_LAUNCH_PERMISSION_REQUIRED);
        }
        if (unverified(ownershipType) && !warnings.contains(OWNERSHIP_UNVERIFIED)) {
            warnings.add(OWNERSHIP_UNVERIFIED);
        }
        return List.copyOf(warnings);
    }
}
