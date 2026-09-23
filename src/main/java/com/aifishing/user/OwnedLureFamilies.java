package com.aifishing.user;

import com.aifishing.common.exception.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class OwnedLureFamilies {

    public static final List<String> IDS = List.of(
            "SOFT_PLASTICS",
            "JIGS",
            "CRANKBAITS",
            "TOPWATER",
            "SPINNERBAITS",
            "JERKBAITS",
            "SPOONS",
            "OTHER"
    );

    private static final Set<String> ALLOWED = Set.copyOf(IDS);

    private OwnedLureFamilies() {
    }

    public static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String id = item.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED.contains(id)) {
                throw new BadRequestException("Unknown owned lure family: " + id);
            }
            unique.add(id);
        }
        return new ArrayList<>(unique);
    }
}
