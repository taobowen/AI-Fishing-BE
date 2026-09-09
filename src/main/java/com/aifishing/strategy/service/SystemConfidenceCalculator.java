package com.aifishing.strategy.service;

import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.context.PipelineReadiness;
import com.aifishing.strategy.domain.DataLimitation;
import com.aifishing.strategy.domain.DataLimitationCode;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.weather.WeatherAvailability;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SystemConfidenceCalculator {

    public static final double MIN = 0.05;
    public static final double MAX = 0.95;
    public static final double BASE = 0.62;
    public static final double WEATHER_AVAILABLE = 0.12;
    public static final double WEATHER_OUT_OF_RANGE = -0.08;
    public static final double WEATHER_MISSING = -0.10;
    public static final double PIPELINE_READY = 0.12;
    public static final double PIPELINE_PARTIAL = 0.04;
    public static final double PIPELINE_NOT_READY = -0.16;
    public static final double STRUCTURE_NONE = -0.10;
    public static final double STRUCTURE_LOW_CONFIDENCE = -0.08;
    public static final double MEAN_CONFIDENCE_WEIGHT = 0.10;
    public static final double SPECIES_CONFIRMED = 0.06;
    public static final double SPECIES_UNCONFIRMED = -0.08;
    public static final double LIMITATION_PENALTY = 0.03;
    public static final double LIMITATION_PENALTY_CAP = 0.20;

    public double calculate(FishingContext context, FishingStrategyProfile profile) {
        double score = BASE;
        score += weather(context);
        score += structure(context);
        score += species(context);
        score -= limitationPenalty(context, profile);
        return clamp(score);
    }

    private double weather(FishingContext context) {
        WeatherAvailability availability = context == null || context.weather() == null
                ? WeatherAvailability.UNAVAILABLE
                : context.weather().availability();
        return switch (availability) {
            case FORECAST_AVAILABLE -> WEATHER_AVAILABLE;
            case OUT_OF_FORECAST_RANGE -> WEATHER_OUT_OF_RANGE;
            case UNAVAILABLE, FAILED -> WEATHER_MISSING;
        };
    }

    private double structure(FishingContext context) {
        if (context == null || context.lake() == null) {
            return PIPELINE_NOT_READY;
        }
        double score = switch (context.lake().pipelineReadiness()) {
            case READY -> PIPELINE_READY;
            case PARTIAL -> PIPELINE_PARTIAL;
            case FAILED, NOT_READY -> PIPELINE_NOT_READY;
        };
        if (has(context, DataLimitationCode.STRUCTURE_NONE_AFTER_ANALYSIS)) {
            score += STRUCTURE_NONE;
        }
        if (has(context, DataLimitationCode.STRUCTURE_DATA_LOW_CONFIDENCE)) {
            score += STRUCTURE_LOW_CONFIDENCE;
        }
        Double mean = context.lake().averageStructureConfidence();
        if (mean != null) {
            score += (mean - 0.5) * MEAN_CONFIDENCE_WEIGHT;
        }
        return score;
    }

    private double species(FishingContext context) {
        if (has(context, DataLimitationCode.TARGET_SPECIES_UNCONFIRMED)) {
            return SPECIES_UNCONFIRMED;
        }
        return SPECIES_CONFIRMED;
    }

    private double limitationPenalty(FishingContext context, FishingStrategyProfile profile) {
        Set<DataLimitationCode> codes = new LinkedHashSet<>();
        if (context != null && context.dataLimitations() != null) {
            for (DataLimitation limitation : context.dataLimitations()) {
                if (limitation != null && limitation.code() != null) {
                    codes.add(limitation.code());
                }
            }
        }
        List<DataLimitation> extras = profile == null ? List.of() : profile.dataLimitations();
        for (DataLimitation limitation : extras) {
            if (limitation != null && limitation.code() != null) {
                codes.add(limitation.code());
            }
        }
        return Math.min(LIMITATION_PENALTY_CAP, codes.size() * LIMITATION_PENALTY);
    }

    private boolean has(FishingContext context, DataLimitationCode code) {
        if (context == null || context.dataLimitations() == null) {
            return false;
        }
        return context.dataLimitations().stream().anyMatch(limitation -> limitation.code() == code)
                || (context.lake() != null && context.lake().dataLimitations().stream()
                .anyMatch(limitation -> limitation.code() == code));
    }

    private double clamp(double score) {
        if (score < MIN) {
            return MIN;
        }
        if (score > MAX) {
            return MAX;
        }
        return Math.round(score * 10000d) / 10000d;
    }
}
