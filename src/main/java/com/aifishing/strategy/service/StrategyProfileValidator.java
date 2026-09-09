package com.aifishing.strategy.service;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.context.LakeStrategyContext;
import com.aifishing.strategy.domain.DataLimitation;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import com.aifishing.strategy.domain.TechniquePreference;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class StrategyProfileValidator {

    static final double STRONG_WEIGHT = 0.6;

    private static final Pattern FEATURE_ID = Pattern.compile("(?i)\\b(feature[_\\s-]?id|lakefeature)\\b");
    private static final Pattern COORDINATE_PAIR = Pattern.compile(
            "\\b-?\\d{1,3}\\.\\d{3,}\\s*,\\s*-?\\d{1,3}\\.\\d{3,}\\b"
    );
    private static final Pattern LAT_LNG_WORD = Pattern.compile("(?i)\\b(latitude|longitude|lat/lng|gps)\\b");
    private static final Pattern WATER_TEMP_NUMBER = Pattern.compile(
            "(?i)water\\s+temp(?:erature)?[^\\n.]{0,40}?\\d+(?:\\.\\d+)?"
    );

    public List<String> validate(FishingStrategyProfile profile, FishingContext context) {
        List<String> errors = new ArrayList<>();
        if (profile == null) {
            errors.add("Strategy profile is missing");
            return errors;
        }
        validateTargets(profile, errors);
        validatePreferences("structurePreferences", profile.structurePreferences(), errors);
        validateTechniques("generalTechniques", profile.generalTechniques(), errors);
        validateWindows(profile, context, errors);
        validateUnavailableStructures(profile, context, errors);
        scanForForbiddenGeography(profile, errors);
        return errors;
    }

    public List<String> warningsForWaterTemperature(FishingStrategyProfile profile, FishingContext context) {
        if (context != null && context.weather() != null && !context.weather().waterTemperatureAvailable()
                && assertsWaterTemperature(profile)) {
            return List.of("Air temperature is not lake water temperature; water temperature is unavailable.");
        }
        return List.of();
    }

    private void validateTargets(FishingStrategyProfile profile, List<String> errors) {
        for (int i = 0; i < profile.targetSpecies().size(); i++) {
            var target = profile.targetSpecies().get(i);
            if (target == null || target.species() == null) {
                errors.add("targetSpecies[" + i + "] has unknown species");
            } else {
                knownEnum(FishSpecies.class, target.species(), "targetSpecies[" + i + "].species", errors);
            }
            if (target != null && target.priority() < 1) {
                errors.add("targetSpecies[" + i + "].priority must be >= 1");
            }
        }
    }

    private void validatePreferences(String field, List<StructurePreference> preferences, List<String> errors) {
        if (preferences == null) {
            errors.add(field + " must be an array of {type, weight}, not a map");
            return;
        }
        for (int i = 0; i < preferences.size(); i++) {
            StructurePreference preference = preferences.get(i);
            String prefix = field + "[" + i + "]";
            if (preference == null || preference.type() == null) {
                errors.add(prefix + ".type is unknown");
            } else {
                knownEnum(FeatureType.class, preference.type(), prefix + ".type", errors);
            }
            validateWeight(prefix, preference == null ? Double.NaN : preference.weight(), errors);
            scanText(prefix + ".rationale", preference == null ? null : preference.rationale(), errors);
        }
    }

    private void validateTechniques(String field, List<TechniquePreference> preferences, List<String> errors) {
        if (preferences == null) {
            errors.add(field + " must be an array of {type, weight}, not a map");
            return;
        }
        for (int i = 0; i < preferences.size(); i++) {
            TechniquePreference preference = preferences.get(i);
            String prefix = field + "[" + i + "]";
            if (preference == null || preference.type() == null) {
                errors.add(prefix + ".type is unknown");
            } else {
                knownEnum(TechniqueType.class, preference.type(), prefix + ".type", errors);
            }
            validateWeight(prefix, preference == null ? Double.NaN : preference.weight(), errors);
            scanText(prefix + ".rationale", preference == null ? null : preference.rationale(), errors);
        }
    }

    private void validateWindows(FishingStrategyProfile profile, FishingContext context, List<String> errors) {
        LocalTime tripStart = context == null || context.trip() == null ? null : context.trip().fishingStartTime();
        LocalTime tripEnd = context == null || context.trip() == null ? null : context.trip().fishingEndTime();
        Double lakeMax = context == null || context.lake() == null ? null : context.lake().maxDepthM();
        List<StrategyTimeWindow> windows = profile.timeWindows();
        for (int i = 0; i < windows.size(); i++) {
            StrategyTimeWindow window = windows.get(i);
            String prefix = "timeWindows[" + i + "]";
            if (window.from() == null || window.to() == null) {
                errors.add(prefix + " requires from and to");
                continue;
            }
            if (!window.from().isBefore(window.to()) && !window.from().equals(window.to())) {
                errors.add(prefix + " from must be before to");
            }
            if (window.from().equals(window.to())) {
                errors.add(prefix + " from must be before to");
            }
            if (tripStart != null && window.from().isBefore(tripStart)) {
                errors.add(prefix + " starts before trip fishingStartTime");
            }
            if (tripEnd != null && window.to().isAfter(tripEnd)) {
                errors.add(prefix + " ends after trip fishingEndTime");
            }
            DepthRange depth = window.preferredDepthM();
            if (depth != null) {
                if (depth.min() > depth.max()) {
                    errors.add(prefix + ".preferredDepthM.min must be <= max");
                }
                if (lakeMax != null && depth.max() > lakeMax) {
                    errors.add(prefix + ".preferredDepthM.max exceeds lake maxDepthM " + lakeMax);
                }
            }
            validatePreferences(prefix + ".structurePreferences", window.structurePreferences(), errors);
            validateTechniques(prefix + ".techniques", window.techniques(), errors);
            for (int j = i + 1; j < windows.size(); j++) {
                StrategyTimeWindow other = windows.get(j);
                if (other.from() == null || other.to() == null) {
                    continue;
                }
                if (overlaps(window, other)) {
                    errors.add(prefix + " overlaps timeWindows[" + j + "]");
                }
            }
        }
        if (profile.weatherInterpretation() != null) {
            scanText("weatherInterpretation.summary", profile.weatherInterpretation().summary(), errors);
        }
        for (int i = 0; i < profile.warnings().size(); i++) {
            scanText("warnings[" + i + "]", profile.warnings().get(i), errors);
        }
    }

    private void validateUnavailableStructures(FishingStrategyProfile profile, FishingContext context, List<String> errors) {
        if (context == null || context.lake() == null) {
            return;
        }
        Set<FeatureType> unavailable = EnumSet.noneOf(FeatureType.class);
        for (LakeStrategyContext.StructureTypeSummary summary : context.lake().structure()) {
            if (!summary.available()) {
                unavailable.add(summary.type());
            }
        }
        checkStrongUnavailable("structurePreferences", profile.structurePreferences(), unavailable, profile, errors);
        for (int i = 0; i < profile.timeWindows().size(); i++) {
            checkStrongUnavailable(
                    "timeWindows[" + i + "].structurePreferences",
                    profile.timeWindows().get(i).structurePreferences(),
                    unavailable,
                    profile,
                    errors
            );
        }
    }

    private void checkStrongUnavailable(
            String field,
            List<StructurePreference> preferences,
            Set<FeatureType> unavailable,
            FishingStrategyProfile profile,
            List<String> errors
    ) {
        for (StructurePreference preference : preferences) {
            if (preference == null || preference.type() == null || !unavailable.contains(preference.type())) {
                continue;
            }
            if (preference.weight() < STRONG_WEIGHT) {
                continue;
            }
            boolean warned = hasWarning(profile, preference.type());
            boolean limited = hasLimitation(profile);
            if (!warned || !limited) {
                errors.add(field + " assigns strong weight to unavailable structure " + preference.type()
                        + " without an explicit warning and typed data limitation");
            }
        }
    }

    private boolean hasWarning(FishingStrategyProfile profile, FeatureType type) {
        String needle = type.name().toLowerCase(Locale.ROOT);
        for (String warning : profile.warnings()) {
            if (warning != null && warning.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        if (profile.weatherInterpretation() != null && profile.weatherInterpretation().summary() != null
                && profile.weatherInterpretation().summary().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return false;
    }

    private boolean hasLimitation(FishingStrategyProfile profile) {
        for (DataLimitation limitation : profile.dataLimitations()) {
            if (limitation == null || limitation.code() == null) {
                continue;
            }
            boolean structureRelated = switch (limitation.code()) {
                case STRUCTURE_NONE_AFTER_ANALYSIS, STRUCTURE_DATA_LOW_CONFIDENCE, STRUCTURE_PIPELINE_NOT_READY,
                     BATHYMETRY_UNAVAILABLE, BATHYMETRY_PARTIAL -> true;
                default -> false;
            };
            if (structureRelated) {
                return true;
            }
        }
        return false;
    }

    private void scanForForbiddenGeography(FishingStrategyProfile profile, List<String> errors) {
        scanText("profile", String.valueOf(profile), errors);
    }

    private void scanText(String field, String text, List<String> errors) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (FEATURE_ID.matcher(text).find()) {
            errors.add(field + " must not mention feature IDs");
        }
        if (COORDINATE_PAIR.matcher(text).find() || LAT_LNG_WORD.matcher(text).find()) {
            errors.add(field + " must not include coordinates or navigation GPS");
        }
    }

    private boolean assertsWaterTemperature(FishingStrategyProfile profile) {
        List<String> texts = new ArrayList<>();
        if (profile.weatherInterpretation() != null) {
            texts.add(profile.weatherInterpretation().summary());
        }
        texts.addAll(profile.warnings());
        for (StructurePreference preference : profile.structurePreferences()) {
            if (preference != null) {
                texts.add(preference.rationale());
            }
        }
        for (TechniquePreference preference : profile.generalTechniques()) {
            if (preference != null) {
                texts.add(preference.rationale());
            }
        }
        for (StrategyTimeWindow window : profile.timeWindows()) {
            for (StructurePreference preference : window.structurePreferences()) {
                texts.add(preference == null ? null : preference.rationale());
            }
            for (TechniquePreference preference : window.techniques()) {
                texts.add(preference == null ? null : preference.rationale());
            }
        }
        for (String text : texts) {
            if (text != null && WATER_TEMP_NUMBER.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(StrategyTimeWindow left, StrategyTimeWindow right) {
        return left.from().isBefore(right.to()) && right.from().isBefore(left.to());
    }

    private void validateWeight(String prefix, double weight, List<String> errors) {
        if (Double.isNaN(weight) || weight < 0 || weight > 1) {
            errors.add(prefix + ".weight must be between 0 and 1");
        }
    }

    private <E extends Enum<E>> void knownEnum(Class<E> type, E value, String field, List<String> errors) {
        try {
            Enum.valueOf(type, value.name());
        } catch (RuntimeException ex) {
            errors.add(field + " is not a known " + type.getSimpleName());
        }
    }
}
