package com.aifishing.planning.filter;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import org.springframework.stereotype.Component;

@Component
public class SafetyFilter implements CandidateFilter {

    @Override
    public FilterResult apply(CandidateSpot candidate, PlanningContext context) {
        WeatherContext weather = context.weather();
        if (weather == null || weather.availability() != WeatherAvailability.FORECAST_AVAILABLE
                || weather.windSpeedKmh() == null) {
            return FilterResult.accept();
        }
        double wind = weather.windSpeedKmh();
        if (context.fishingMode() == FishingMode.BOAT
                && wind >= context.properties().getSafety().getWindHardRejectKmh()) {
            return FilterResult.reject(RejectionReason.SAFETY_HARD_REJECT);
        }
        if (wind >= context.properties().getSafety().getWindPenaltyKmh()) {
            candidate.setWindPenalty(true);
            candidate.addWarning("WIND_PENALTY");
            return FilterResult.accept("WIND_PENALTY");
        }
        return FilterResult.accept();
    }
}
