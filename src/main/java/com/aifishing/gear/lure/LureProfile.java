package com.aifishing.gear.lure;

import com.aifishing.common.enums.LureColorFamily;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.LureLengthBand;
import com.aifishing.common.enums.LureWeightBand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record LureProfile(
        LureFamily lureFamily,
        LureLengthBand lengthBand,
        Double lengthInches,
        LureWeightBand weightBand,
        Double weightOz,
        List<LureColorFamily> colors,
        String customLabel
) {
    public static final String METADATA_KEY = "lureProfile";

    public LureProfile {
        colors = colors == null ? List.of() : List.copyOf(colors);
        if (customLabel != null && customLabel.isBlank()) {
            customLabel = null;
        }
    }

    public boolean requiresLength() {
        return lureFamily != null && lureFamily.requiresLength();
    }

    public boolean requiresWeight() {
        return lureFamily != null && lureFamily.requiresWeight();
    }

    public String sizeLabel() {
        if (requiresWeight() && weightBand != null) {
            return weightBand.displayName();
        }
        if (lengthBand != null) {
            return lengthBand.displayName();
        }
        if (weightBand != null) {
            return weightBand.displayName();
        }
        return null;
    }

    public String autoName() {
        if (customLabel != null && !customLabel.isBlank()) {
            return customLabel.trim();
        }
        List<String> parts = new ArrayList<>();
        if (lureFamily != null) {
            parts.add(lureFamily.displayName());
        }
        String size = sizeLabel();
        if (size != null) {
            parts.add(size);
        }
        if (!colors.isEmpty()) {
            parts.add(String.join(" / ", colors.stream().map(LureColorFamily::displayName).toList()));
        }
        return String.join(" · ", parts);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (lureFamily != null) {
            map.put("lureFamily", lureFamily.name());
        }
        if (lengthBand != null) {
            map.put("lengthBand", lengthBand.wire());
        }
        if (lengthInches != null) {
            map.put("lengthInches", lengthInches);
        }
        if (weightBand != null) {
            map.put("weightBand", weightBand.wire());
        }
        if (weightOz != null) {
            map.put("weightOz", weightOz);
        }
        if (!colors.isEmpty()) {
            map.put("colors", colors.stream().map(Enum::name).toList());
        }
        if (customLabel != null) {
            map.put("customLabel", customLabel);
        }
        return map;
    }

    public static LureProfile fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        LureFamily family = enumValue(LureFamily.class, map.get("lureFamily"));
        if (family == null) {
            return null;
        }
        return new LureProfile(
                family,
                lengthBand(map.get("lengthBand")),
                doubleValue(map.get("lengthInches")),
                weightBand(map.get("weightBand")),
                doubleValue(map.get("weightOz")),
                colorList(map.get("colors")),
                stringValue(map.get("customLabel"))
        );
    }

    @SuppressWarnings("unchecked")
    public static LureProfile fromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object nested = metadata.get(METADATA_KEY);
        if (nested instanceof Map<?, ?> raw) {
            return fromMap((Map<String, Object>) raw);
        }
        if (metadata.containsKey("lureFamily")) {
            return fromMap(metadata);
        }
        return null;
    }

    public static Map<String, Object> mergeInto(Map<String, Object> metadata, LureProfile profile) {
        Map<String, Object> next = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        if (profile == null) {
            next.remove(METADATA_KEY);
            return next;
        }
        next.put(METADATA_KEY, profile.toMap());
        return next;
    }

    private static LureLengthBand lengthBand(Object value) {
        String text = stringValue(value);
        return text == null ? null : LureLengthBand.from(text);
    }

    private static LureWeightBand weightBand(Object value) {
        String text = stringValue(value);
        return text == null ? null : LureWeightBand.from(text);
    }

    private static List<LureColorFamily> colorList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<LureColorFamily> colors = new ArrayList<>();
        for (Object item : list) {
            LureColorFamily color = enumValue(LureColorFamily.class, item);
            if (color != null) {
                colors.add(color);
            }
        }
        return colors;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value) {
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }
}
