package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.common.enums.PropulsionType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BoatConfigurationFingerprinter {

    private BoatConfigurationFingerprinter() {
    }

    public static Fingerprint fingerprint(Boat boat) {
        return fingerprint(boat, null);
    }

    public static Fingerprint fingerprint(Boat boat, BoatCapabilityPriors priors) {
        Map<String, Object> normalized = normalized(boat);
        if (priors != null && priors.present()) {
            normalized.put("priorMetrics", priors.toMap());
        }
        return new Fingerprint(sha256(canonicalJson(normalized)), normalized);
    }

    public static Map<String, Object> normalized(Boat boat) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("boatType", boat.getType() == null ? null : boat.getType().name());
        map.put("manufacturer", norm(boat.getManufacturer()));
        map.put("model", norm(boat.getModel()));
        map.put("year", boat.getYear());
        List<String> types = (boat.getPropulsionTypes() == null ? List.<PropulsionType>of() : boat.getPropulsionTypes())
                .stream()
                .map(Enum::name)
                .sorted()
                .toList();
        map.put("propulsionTypes", types);
        map.put("primaryTransitPropulsionType",
                boat.getPrimaryTransitPropulsionType() == null ? null : boat.getPrimaryTransitPropulsionType().name());
        List<Map<String, Object>> motors = new ArrayList<>();
        for (BoatMotor motor : sortedMotors(boat)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("propulsionType", motor.propulsionType() == null ? null : motor.propulsionType().name());
            row.put("manufacturer", norm(motor.manufacturer()));
            row.put("model", norm(motor.model()));
            row.put("horsepower", decimal(motor.horsepower()));
            row.put("thrustLb", decimal(motor.thrustLb()));
            motors.add(row);
        }
        map.put("motors", motors);
        Map<String, Object> facts = EquipmentFactsSanitizer.extract(boat.getConfigurationDescription());
        if (!facts.isEmpty()) {
            map.put("sanitizedEquipmentFacts", facts);
        }
        return map;
    }

    private static List<BoatMotor> sortedMotors(Boat boat) {
        List<BoatMotor> motors = new ArrayList<>(boat.getMotors() == null ? List.of() : boat.getMotors());
        motors.sort(Comparator.comparing(motor -> motor.propulsionType() == null ? "" : motor.propulsionType().name()));
        return motors;
    }

    private static String norm(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static String canonicalJson(Map<String, Object> normalized) {
        StringBuilder builder = new StringBuilder();
        appendValue(builder, normalized);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (var entry : ((Map<String, Object>) map).entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(entry.getKey()).append("\":");
                appendValue(builder, entry.getValue());
            }
            builder.append('}');
            return;
        }
        if (value instanceof List<?> list) {
            builder.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                appendValue(builder, item);
            }
            builder.append(']');
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }
        builder.append('"').append(value.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Fingerprint(String hex, Map<String, Object> normalizedConfiguration) {
    }
}
