package com.aifishing.strategy.service;

import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.domain.StrategyRunStatus;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.strategy.weather.WeatherContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class StrategyRunPersistence {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final StrategyRunRepository repository;
    private final ObjectMapper objectMapper;

    public StrategyRunPersistence(StrategyRunRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StrategyRun insertRunning(UUID tripId, Pipeline pipeline, String modelId, String promptVersion) {
        StrategyRun run = new StrategyRun();
        run.setTripId(tripId);
        run.setStatus(StrategyRunStatus.RUNNING);
        run.setFeaturePipeline(pipeline);
        run.setModelId(modelId);
        run.setPromptVersion(promptVersion);
        run.setStartedAt(Instant.now());
        return repository.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StrategyRun complete(
            UUID runId,
            Object fishingContext,
            WeatherContext weather,
            FishingStrategyProfile profile,
            Map<String, Object> sourceMetadata,
            Map<String, Object> usageMetadata,
            String featureAnalysisVersion
    ) {
        StrategyRun run = repository.findById(runId).orElseThrow();
        run.setStatus(StrategyRunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        run.setFeatureAnalysisVersion(featureAnalysisVersion);
        run.setFishingContext(toMap(fishingContext));
        run.setWeatherSnapshot(toMap(weather));
        run.setStrategyProfile(toMap(profile));
        run.setSourceMetadata(sourceMetadata);
        run.setUsageMetadata(usageMetadata);
        run.setErrorMessage(null);
        return repository.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StrategyRun fail(
            UUID runId,
            Object fishingContext,
            WeatherContext weather,
            String errorMessage,
            Map<String, Object> sourceMetadata,
            Map<String, Object> usageMetadata,
            String featureAnalysisVersion
    ) {
        StrategyRun run = repository.findById(runId).orElseThrow();
        run.setStatus(StrategyRunStatus.FAILED);
        run.setCompletedAt(Instant.now());
        if (featureAnalysisVersion != null) {
            run.setFeatureAnalysisVersion(featureAnalysisVersion);
        }
        if (fishingContext != null) {
            run.setFishingContext(toMap(fishingContext));
        }
        if (weather != null) {
            run.setWeatherSnapshot(toMap(weather));
        }
        run.setSourceMetadata(sourceMetadata);
        run.setUsageMetadata(usageMetadata);
        run.setErrorMessage(truncate(errorMessage));
        return repository.saveAndFlush(run);
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, MAP);
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
