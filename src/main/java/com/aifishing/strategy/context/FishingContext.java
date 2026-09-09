package com.aifishing.strategy.context;

import com.aifishing.strategy.domain.DataLimitation;
import com.aifishing.strategy.weather.WeatherContext;

import java.util.List;

public record FishingContext(
        TripContext trip,
        UserFishingContext user,
        LakeStrategyContext lake,
        WeatherContext weather,
        List<DataLimitation> dataLimitations
) {
    public FishingContext {
        dataLimitations = dataLimitations == null ? List.of() : List.copyOf(dataLimitations);
    }
}
