package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.WindWaveCapability;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class EffectiveBoatCapabilityFactory {

    private final BoatCapabilityProperties capabilityProperties;

    public EffectiveBoatCapabilityFactory(BoatCapabilityProperties capabilityProperties) {
        this.capabilityProperties = capabilityProperties;
    }

    public EffectiveBoatCapability from(
            Boat boat,
            ResolvedBoatCapability baseline,
            WeatherContext weather,
            PlanningProperties planning
    ) {
        WindWaveCapability windWave = baseline.windWaveCapability() == null || baseline.windWaveCapability().value() == null
                ? WindWaveCapability.LOW
                : baseline.windWaveCapability().value();
        double derate = 0;
        Map<String, Object> weatherAdjustment = new LinkedHashMap<>();
        if (weather != null
                && weather.availability() == WeatherAvailability.FORECAST_AVAILABLE
                && weather.windSpeedKmh() != null
                && weather.windSpeedKmh() >= planning.getSafety().getWindPenaltyKmh()) {
            derate = capabilityProperties.getWindDerate().fraction(windWave);
            weatherAdjustment.put("windSpeedKmh", weather.windSpeedKmh());
            weatherAdjustment.put("derateFraction", derate);
            weatherAdjustment.put("windWaveCapability", windWave.name());
        }
        double cruise = baseline.cruiseSpeedKmh() != null && baseline.cruiseSpeedKmh().value() != null
                ? baseline.cruiseSpeedKmh().value()
                : planning.getTravel().getDefaultBoatKmh();
        cruise = cruise * (1.0 - derate);
        Double estimated = baseline.estimatedPracticalRangeKm() == null ? null : baseline.estimatedPracticalRangeKm().value();
        boolean reliable = BoatCapabilityRangeMath.rangeReliable(
                baseline.estimatedPracticalRangeKm() == null ? null : baseline.estimatedPracticalRangeKm().confidence(),
                capabilityProperties.getLowRangeConfidenceThreshold()
        );
        Double systemUsable = BoatCapabilityRangeMath.systemUsableRangeKm(
                estimated,
                capabilityProperties.getRangeReserveFraction(),
                reliable
        );
        if (systemUsable != null) {
            systemUsable = systemUsable * (1.0 - derate);
        }
        Double comfortable = boat.getComfortableRoundTripRangeKm() == null
                ? null
                : boat.getComfortableRoundTripRangeKm().doubleValue();
        Double effectiveUsable = BoatCapabilityRangeMath.effectiveUsableRangeKm(systemUsable, comfortable);
        boolean rangeEnforced = effectiveUsable != null && effectiveUsable > 0;
        double maxLegKm = rangeEnforced
                ? effectiveUsable
                : planning.maxOneWayKm(boat.getType());
        return new EffectiveBoatCapability(
                cruise,
                estimated,
                systemUsable,
                comfortable,
                effectiveUsable,
                rangeEnforced,
                maxLegKm,
                windWave,
                derate,
                weatherAdjustment
        );
    }
}
