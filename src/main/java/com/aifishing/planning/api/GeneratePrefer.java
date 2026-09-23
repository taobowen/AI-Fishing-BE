package com.aifishing.planning.api;

public final class GeneratePrefer {

    private GeneratePrefer() {
    }

    public static boolean respondAsync(String prefer) {
        if (prefer == null || prefer.isBlank()) {
            return false;
        }
        for (String part : prefer.split(",")) {
            String token = part.split(";", 2)[0].trim();
            if (token.equalsIgnoreCase("respond-async")) {
                return true;
            }
        }
        return false;
    }
}
