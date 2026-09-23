package com.aifishing.strategy.service;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.processing.OpenAiProperties;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.strategy.StrategyProperties;
import com.aifishing.strategy.ai.FishingStrategyReasoner;
import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.context.FishingContextBuilder;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.domain.StrategyRunStatus;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FishingStrategyService {

    private static final Logger log = LoggerFactory.getLogger(FishingStrategyService.class);

    private final TripRepository tripRepository;
    private final StrategyRunRepository strategyRunRepository;
    private final StrategyRunPersistence persistence;
    private final FishingContextBuilder contextBuilder;
    private final FishingStrategyReasoner reasoner;
    private final StrategyProfileValidator validator;
    private final SystemConfidenceCalculator confidenceCalculator;
    private final OpenAiProperties openAiProperties;
    private final StrategyProperties strategyProperties;

    public FishingStrategyService(
            TripRepository tripRepository,
            StrategyRunRepository strategyRunRepository,
            StrategyRunPersistence persistence,
            FishingContextBuilder contextBuilder,
            FishingStrategyReasoner reasoner,
            StrategyProfileValidator validator,
            SystemConfidenceCalculator confidenceCalculator,
            OpenAiProperties openAiProperties,
            StrategyProperties strategyProperties
    ) {
        this.tripRepository = tripRepository;
        this.strategyRunRepository = strategyRunRepository;
        this.persistence = persistence;
        this.contextBuilder = contextBuilder;
        this.reasoner = reasoner;
        this.validator = validator;
        this.confidenceCalculator = confidenceCalculator;
        this.openAiProperties = openAiProperties;
        this.strategyProperties = strategyProperties;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StrategyRun generate(UUID tripId) {
        return generate(tripId, strategyProperties.getFeaturePipeline());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StrategyRun generate(UUID tripId, Pipeline featurePipeline) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        Pipeline pipeline = featurePipeline == null ? strategyProperties.getFeaturePipeline() : featurePipeline;
        StrategyRun running = persistence.insertRunning(
                trip.getId(),
                pipeline,
                openAiProperties.getStrategyModel(),
                openAiProperties.getStrategyPromptVersion()
        );
        FishingContext context = null;
        try {
            context = contextBuilder.build(trip, pipeline);
            if (contextBuilder.pipelineNotReady(context)) {
                return persistence.fail(
                        running.getId(),
                        context,
                        context.weather(),
                        "Structure pipeline is " + context.lake().pipelineReadiness()
                                + "; last successful strategy is preserved if one exists.",
                        Map.of("featurePipeline", pipeline.name()),
                        Map.of(),
                        context.lake().analysisVersion()
                );
            }
            FishingStrategyReasoner.StrategyReasonerResult result = reason(context, List.of());
            List<String> errors = validator.validate(result.profile(), context);
            if (!errors.isEmpty()) {
                log.info("Retrying strategy reasoner after validation errors: {}", errors);
                GenerateProfiler.current().count("strategyAiRetries");
                result = reason(context, errors);
                errors = validator.validate(result.profile(), context);
            }
            if (!errors.isEmpty()) {
                return persistence.fail(
                        running.getId(),
                        context,
                        context.weather(),
                        "Strategy profile failed validation: " + String.join("; ", errors),
                        result.sourceMetadata(),
                        result.usageMetadata(),
                        context.lake().analysisVersion()
                );
            }
            List<String> extraWarnings = new ArrayList<>(validator.warningsForWaterTemperature(result.profile(), context));
            double systemConfidence = confidenceCalculator.calculate(context, result.profile());
            FishingStrategyProfile profile = result.profile().withBackendFields(
                    systemConfidence,
                    context.dataLimitations(),
                    extraWarnings
            );
            return persistence.complete(
                    running.getId(),
                    context,
                    context.weather(),
                    profile,
                    result.sourceMetadata(),
                    result.usageMetadata(),
                    context.lake().analysisVersion()
            );
        } catch (Exception ex) {
            log.warn("Strategy generation failed for trip {}: {}", tripId, ex.getMessage());
            return persistence.fail(
                    running.getId(),
                    context,
                    context == null ? null : context.weather(),
                    ex.getMessage(),
                    Map.of(),
                    Map.of(),
                    context == null ? null : context.lake().analysisVersion()
            );
        }
    }

    public FishingContext context(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        return contextBuilder.build(trip);
    }

    public StrategyRun latestCompleted(UUID tripId) {
        requireTrip(tripId);
        return strategyRunRepository.findFirstByTripIdAndStatusOrderByCompletedAtDesc(tripId, StrategyRunStatus.COMPLETED)
                .orElseThrow(() -> new NotFoundException("No completed strategy for trip"));
    }

    public List<StrategyRun> runs(UUID tripId) {
        requireTrip(tripId);
        return strategyRunRepository.findByTripIdOrderByStartedAtDesc(tripId);
    }

    private void requireTrip(UUID tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new NotFoundException("Trip not found");
        }
    }

    private FishingStrategyReasoner.StrategyReasonerResult reason(FishingContext context, List<String> errors) {
        GenerateProfiler.current().count("strategyAiCalls");
        GenerateProfiler.current().start(GenerateProfiler.STRATEGY_AI);
        try {
            return reasoner.reason(context, errors);
        } finally {
            GenerateProfiler.current().end(GenerateProfiler.STRATEGY_AI);
        }
    }
}
