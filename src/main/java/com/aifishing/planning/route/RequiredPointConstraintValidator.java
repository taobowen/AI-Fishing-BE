package com.aifishing.planning.route;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.filter.AccessibilityFilter;
import com.aifishing.planning.filter.BoatCapabilityFilter;
import com.aifishing.planning.filter.CandidateFilter;
import com.aifishing.planning.filter.FilterResult;
import com.aifishing.planning.filter.RejectionReason;
import com.aifishing.planning.filter.SafetyFilter;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.weather.WeatherAvailability;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pre-search validation for Required Points (access / range / safety / weather / launch).
 * A single failure fails the run with {@link RoutePlanConstraints#REQUIRED_POINT_INFEASIBLE}.
 */
@Component
public class RequiredPointConstraintValidator {

    private final List<CandidateFilter> filters;
    private final SafetyFilter safetyFilter;

    public RequiredPointConstraintValidator(
            AccessibilityFilter accessibilityFilter,
            BoatCapabilityFilter boatCapabilityFilter,
            SafetyFilter safetyFilter
    ) {
        this.safetyFilter = safetyFilter;
        this.filters = List.of(accessibilityFilter, boatCapabilityFilter, safetyFilter);
    }

    public void validate(List<CandidateSpot> required, PlanningContext context) {
        if (required == null || required.isEmpty()) {
            return;
        }
        for (CandidateSpot spot : required) {
            String weatherReason = tripWeatherReason(context);
            if (weatherReason != null) {
                throw infeasible(weatherReason, spot);
            }
            for (CandidateFilter filter : filters) {
                FilterResult result = filter.apply(spot, context);
                if (!result.accepted()) {
                    throw infeasible(mapReason(result.reason(), filter), spot);
                }
            }
        }
    }

    private String tripWeatherReason(PlanningContext context) {
        if (context == null || context.weather() == null) {
            return null;
        }
        if (context.weather().availability() != WeatherAvailability.FORECAST_AVAILABLE) {
            return null;
        }
        FilterResult safety = safetyFilter.apply(dummyForWeather(), context);
        if (!safety.accepted() && safety.reason() == RejectionReason.SAFETY_HARD_REJECT) {
            // Global hard wind is weather/safety for every required point.
            return "WEATHER";
        }
        return null;
    }

    private static CandidateSpot dummyForWeather() {
        CandidateSpot spot = new CandidateSpot();
        spot.setLocation(null);
        return spot;
    }

    static String mapReason(RejectionReason reason, CandidateFilter filter) {
        if (reason == null) {
            return "ACCESS";
        }
        return switch (reason) {
            case BOAT_TRAVEL_UNREASONABLE -> "RANGE";
            case SAFETY_HARD_REJECT -> filter instanceof SafetyFilter ? "WEATHER" : "SAFETY";
            case OUTSIDE_LAKE, ON_ISLAND, INVALID_GEOMETRY, INVALID_LOCATION,
                    SHORE_INACCESSIBLE, NO_WATER_POINT -> "ACCESS";
            default -> reason.name();
        };
    }

    private static RequiredPointInfeasibleException infeasible(String reason, CandidateSpot spot) {
        return new RequiredPointInfeasibleException(reason, spot);
    }

    public static final class RequiredPointInfeasibleException extends RuntimeException {
        private final String pointReason;

        public RequiredPointInfeasibleException(String pointReason, CandidateSpot spot) {
            super(RoutePlanConstraints.REQUIRED_POINT_INFEASIBLE + ":" + pointReason
                    + (spot == null || spot.planningIdentity() == null
                    ? ""
                    : ":" + spot.planningIdentity()));
            this.pointReason = pointReason;
        }

        public String pointReason() {
            return pointReason;
        }

        public String errorCode() {
            return RoutePlanConstraints.REQUIRED_POINT_INFEASIBLE;
        }
    }
}
