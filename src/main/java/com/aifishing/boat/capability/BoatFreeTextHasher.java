package com.aifishing.boat.capability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public final class BoatFreeTextHasher {

    private BoatFreeTextHasher() {
    }

    public static String hash(String text) {
        String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static boolean changed(String previousHash, String nextHash) {
        return !Objects.equals(previousHash == null ? "" : previousHash, nextHash == null ? "" : nextHash);
    }
}
