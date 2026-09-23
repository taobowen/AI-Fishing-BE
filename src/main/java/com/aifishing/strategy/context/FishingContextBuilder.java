package com.aifishing.strategy.context;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.strategy.domain.DataLimitation;
import com.aifishing.strategy.domain.DataLimitationCode;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.strategy.weather.WeatherService;
import com.aifishing.trip.domain.Trip;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Component
public class FishingContextBuilder {

    private final LakeRepository lakeRepository;
    private final LakeContextBuilder lakeContextBuilder;
    private final UserFishingContextBuilder userFishingContextBuilder;
    private final WeatherService weatherService;

    public FishingContextBuilder(
            LakeRepository lakeRepository,
            LakeContextBuilder lakeContextBuilder,
            UserFishingContextBuilder userFishingContextBuilder,
            WeatherService weatherService
    ) {
        this.lakeRepository = lakeRepository;
        this.lakeContextBuilder = lakeContextBuilder;
        this.userFishingContextBuilder = userFishingContextBuilder;
        this.weatherService = weatherService;
    }

    public FishingContext build(Trip trip) {
        return build(trip, null);
    }

    public FishingContext build(Trip trip, Pipeline featurePipeline) {
        GenerateProfiler.current().start(GenerateProfiler.STRATEGY_CONTEXT);
        Lake lake = lakeRepository.findById(trip.getLakeId())
                .orElseThrow(() -> new IllegalStateException("Lake not found for trip " + trip.getId()));
        var window = com.aifishing.planning.environment.TripClock.resolve(trip, lake);
        TripContext tripContext = new TripContext(
                trip.getId(),
                trip.getPlannedDate(),
                window.plannedEndDate(),
                trip.getFishingStartTime(),
                trip.getFishingEndTime(),
                lake.getTimeZoneId(),
                trip.getPrimaryTargetSpecies(),
                trip.getSecondaryTargetSpecies(),
                trip.getFishingMode(),
                trip.getBoatId()
        );
        UserFishingContext user = userFishingContextBuilder.build(trip.getUserId(), trip.getBoatId());
        LakeStrategyContext lakeContext = lakeContextBuilder.build(lake, featurePipeline);
        GenerateProfiler.current().end(GenerateProfiler.STRATEGY_CONTEXT);
        GenerateProfiler.current().start(GenerateProfiler.WEATHER_RESOLVE);
        WeatherContext weather = weather(lake, trip);
        GenerateProfiler.current().end(GenerateProfiler.WEATHER_RESOLVE);
        List<DataLimitation> limitations = mergeLimitations(trip, lakeContext, weather);
        return new FishingContext(tripContext, user, lakeContext, weather, limitations);
    }

    public boolean pipelineNotReady(FishingContext context) {
        PipelineReadiness readiness = context.lake().pipelineReadiness();
        return readiness == PipelineReadiness.FAILED || readiness == PipelineReadiness.NOT_READY;
    }

    private WeatherContext weather(Lake lake, Trip trip) {
        Point centroid = lake.getCentroid();
        double latitude = centroid.getY();
        double longitude = centroid.getX();
        var window = com.aifishing.planning.environment.TripClock.resolve(trip, lake);
        return weatherService.forTrip(
                latitude,
                longitude,
                lake.getTimeZoneId(),
                window.plannedDate(),
                window.plannedEndDate(),
                trip.getFishingStartTime(),
                trip.getFishingEndTime()
        );
    }

    private List<DataLimitation> mergeLimitations(Trip trip, LakeStrategyContext lake, WeatherContext weather) {
        LinkedHashMap<DataLimitationCode, DataLimitation> merged = new LinkedHashMap<>();
        for (DataLimitation limitation : lake.dataLimitations()) {
            merged.putIfAbsent(limitation.code(), limitation);
        }
        WeatherAvailability availability = weather == null ? WeatherAvailability.UNAVAILABLE : weather.availability();
        switch (availability) {
            case OUT_OF_FORECAST_RANGE -> merged.putIfAbsent(
                    DataLimitationCode.WEATHER_OUT_OF_FORECAST_RANGE,
                    new DataLimitation(DataLimitationCode.WEATHER_OUT_OF_FORECAST_RANGE, weather.notes())
            );
            case UNAVAILABLE -> merged.putIfAbsent(
                    DataLimitationCode.WEATHER_UNAVAILABLE,
                    new DataLimitation(DataLimitationCode.WEATHER_UNAVAILABLE, weather.notes())
            );
            case FAILED -> merged.putIfAbsent(
                    DataLimitationCode.WEATHER_FAILED,
                    new DataLimitation(DataLimitationCode.WEATHER_FAILED, weather.notes())
            );
            case FORECAST_AVAILABLE -> {
            }
        }
        if (weather == null || !weather.waterTemperatureAvailable()) {
            merged.putIfAbsent(
                    DataLimitationCode.WATER_TEMPERATURE_UNAVAILABLE,
                    new DataLimitation(
                            DataLimitationCode.WATER_TEMPERATURE_UNAVAILABLE,
                            "Air temperature is not observed lake water temperature; waterTemperatureC is unavailable."
                    )
            );
        }
        Set<FishSpecies> confirmed = lakeContextBuilder.confirmedSpecies(lake);
        if (trip.getPrimaryTargetSpecies() != null && !confirmed.contains(trip.getPrimaryTargetSpecies())) {
            merged.putIfAbsent(
                    DataLimitationCode.TARGET_SPECIES_UNCONFIRMED,
                    new DataLimitation(
                            DataLimitationCode.TARGET_SPECIES_UNCONFIRMED,
                            "Primary target " + trip.getPrimaryTargetSpecies() + " is not in canonical lake species or stocking records."
                    )
            );
        }
        return new ArrayList<>(merged.values());
    }
}
