package com.aifishing.planning.environment;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.service.PlanningContext;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BoatWeatherPenalty {

    public boolean hardReject(WeatherSample sample, PlanningContext context) {
        if (context.fishingMode() != FishingMode.BOAT) {
            return false;
        }
        Double wind = sample == null ? null : sample.windSpeedKmh();
        if (wind == null) {
            return false;
        }
        return wind >= context.properties().getSafety().getWindHardRejectKmh();
    }

    public boolean travelIntervalHardReject(TimeIndexedWeather weather, Instant depart, Instant arrive, PlanningContext context) {
        if (!weather.forecastAvailable()) {
            return false;
        }
        return hardReject(weather.intervalMaximumWind(depart, arrive), context);
    }

    /**
     * Soft penalty in [0, 1] subtracted from utility. Hard reject is handled separately.
     */
    public double penalty(WeatherSample sample, PlanningContext context) {
        if (context.fishingMode() != FishingMode.BOAT) {
            return 0;
        }
        Double wind = sample == null ? null : sample.windSpeedKmh();
        if (wind == null) {
            return 0;
        }
        PlanningProperties.Safety safety = context.properties().getSafety();
        if (wind < safety.getWindPenaltyKmh()) {
            return 0;
        }
        if (wind >= safety.getWindHardRejectKmh()) {
            return 1;
        }
        double span = Math.max(1, safety.getWindHardRejectKmh() - safety.getWindPenaltyKmh());
        return Math.max(0, Math.min(1, (wind - safety.getWindPenaltyKmh()) / span));
    }
}
