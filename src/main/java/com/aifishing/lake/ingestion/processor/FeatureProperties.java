package com.aifishing.lake.ingestion.processor;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class FeatureProperties {

    private FeatureProperties() {
    }

    public static String text(JsonNode properties, String... names) {
        if (properties == null) {
            return null;
        }
        for (String name : names) {
            if (properties.hasNonNull(name)) {
                String value = properties.get(name).asText();
                if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    public static BigDecimal decimal(JsonNode properties, String... names) {
        String value = text(properties, names);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Integer integer(JsonNode properties, String... names) {
        String value = text(properties, names);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "")).intValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Long longValue(JsonNode properties, String... names) {
        String value = text(properties, names);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "")).longValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Boolean bool(JsonNode properties, String... names) {
        String value = text(properties, names);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("y") || normalized.equals("yes") || normalized.equals("true") || normalized.equals("1")) {
            return true;
        }
        if (normalized.equals("n") || normalized.equals("no") || normalized.equals("false") || normalized.equals("0")) {
            return false;
        }
        return null;
    }

    public static LocalDate date(JsonNode properties, String... names) {
        String value = text(properties, names);
        if (value == null) {
            return null;
        }
        try {
            if (value.length() >= 10) {
                return LocalDate.parse(value.substring(0, 10));
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
