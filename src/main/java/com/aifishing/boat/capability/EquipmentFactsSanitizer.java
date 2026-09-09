package com.aifishing.boat.capability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Materializes non-personal equipment facts from free-text configuration.
 * Raw user prose is never stored in the shared cache.
 */
public final class EquipmentFactsSanitizer {

    private static final Pattern VOLTS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*v\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMP_HOURS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*ah\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUEL_LITERS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*l(?:itre|iter)?s?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIFEPO = Pattern.compile("lifepo4|li[- ]?ion|lithium", Pattern.CASE_INSENSITIVE);

    private EquipmentFactsSanitizer() {
    }

    public static Map<String, Object> extract(String configurationDescription) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (configurationDescription == null || configurationDescription.isBlank()) {
            return facts;
        }
        String text = configurationDescription.toLowerCase(Locale.ROOT);
        Matcher volts = VOLTS.matcher(text);
        if (volts.find()) {
            facts.put("batteryVoltageV", Double.parseDouble(volts.group(1)));
        }
        Matcher ah = AMP_HOURS.matcher(text);
        if (ah.find()) {
            facts.put("batteryCapacityAh", Double.parseDouble(ah.group(1)));
        }
        if (LIFEPO.matcher(text).find()) {
            if (text.contains("lifepo4")) {
                facts.put("batteryChemistry", "LiFePO4");
            } else {
                facts.put("batteryChemistry", "lithium");
            }
        }
        Matcher fuel = FUEL_LITERS.matcher(text);
        if (fuel.find() && (text.contains("fuel") || text.contains("tank") || text.contains("petrol") || text.contains("gas"))) {
            facts.put("portableFuelTankL", Double.parseDouble(fuel.group(1)));
        }
        return facts;
    }
}
