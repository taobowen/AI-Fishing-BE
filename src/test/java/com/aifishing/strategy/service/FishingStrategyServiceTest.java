package com.aifishing.strategy.service;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripStatus;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.processing.OpenAiProperties;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.StrategyProperties;
import com.aifishing.strategy.ai.FishingStrategyReasoner;
import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.context.FishingContextBuilder;
import com.aifishing.strategy.context.PipelineReadiness;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.domain.StrategyRunStatus;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FishingStrategyServiceTest {

    @Mock TripRepository tripRepository;
    @Mock StrategyRunRepository strategyRunRepository;
    @Mock StrategyRunPersistence persistence;
    @Mock FishingContextBuilder contextBuilder;
    @Mock FishingStrategyReasoner reasoner;
    @Mock StrategyProfileValidator validator;
    @Mock SystemConfidenceCalculator confidenceCalculator;

    private FishingStrategyService service;
    private final UUID tripId = StrategyFixtures.TRIP_ID;
    private final UUID runId = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @BeforeEach
    void setUp() {
        service = new FishingStrategyService(
                tripRepository,
                strategyRunRepository,
                persistence,
                contextBuilder,
                reasoner,
                validator,
                confidenceCalculator,
                new OpenAiProperties(),
                new StrategyProperties()
        );
        org.mockito.Mockito.lenient().when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip()));
        StrategyRun running = new StrategyRun();
        running.setId(runId);
        running.setTripId(tripId);
        running.setStatus(StrategyRunStatus.RUNNING);
        org.mockito.Mockito.lenient().when(persistence.insertRunning(eq(tripId), any(), any(), any())).thenReturn(running);
    }

    @Test
    void missingTripIsNotFound() {
        org.mockito.Mockito.when(tripRepository.findById(tripId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(tripId)).isInstanceOf(NotFoundException.class);
        verify(persistence, never()).insertRunning(any(), any(), any(), any());
    }

    @Test
    void pipelineNotReadyFailsWithoutCallingReasoner() {
        FishingContext context = StrategyFixtures.context(
                PipelineReadiness.NOT_READY, WeatherAvailability.FORECAST_AVAILABLE, true, 0, null, 12.0);
        when(contextBuilder.build(any(), any())).thenReturn(context);
        when(contextBuilder.pipelineNotReady(context)).thenReturn(true);
        StrategyRun failed = failedRun();
        when(persistence.fail(eq(runId), eq(context), any(), any(), any(), any(), any())).thenReturn(failed);

        StrategyRun result = service.generate(tripId);
        assertThat(result.getStatus()).isEqualTo(StrategyRunStatus.FAILED);
        verify(reasoner, never()).reason(any(), any());
    }

    @Test
    void pipelineFailedFailsWithoutCallingReasoner() {
        FishingContext context = StrategyFixtures.context(
                PipelineReadiness.FAILED, WeatherAvailability.FORECAST_AVAILABLE, true, 0, null, 12.0);
        when(contextBuilder.build(any(), any())).thenReturn(context);
        when(contextBuilder.pipelineNotReady(context)).thenReturn(true);
        when(persistence.fail(eq(runId), eq(context), any(), any(), any(), any(), any())).thenReturn(failedRun());
        service.generate(tripId);
        verify(reasoner, never()).reason(any(), any());
    }

    @Test
    void zeroFeaturesAfterReadyAnalysisStillCompletes() {
        FishingContext context = StrategyFixtures.context(
                PipelineReadiness.READY, WeatherAvailability.FORECAST_AVAILABLE, true, 0, null, 12.0);
        when(contextBuilder.build(any(), any())).thenReturn(context);
        when(contextBuilder.pipelineNotReady(context)).thenReturn(false);
        when(reasoner.reason(eq(context), eq(List.of()))).thenReturn(
                new FishingStrategyReasoner.StrategyReasonerResult(StrategyFixtures.validProfile(), null, null));
        when(validator.validate(any(), eq(context))).thenReturn(List.of());
        when(confidenceCalculator.calculate(eq(context), any())).thenReturn(0.41);
        StrategyRun completed = new StrategyRun();
        completed.setId(runId);
        completed.setStatus(StrategyRunStatus.COMPLETED);
        when(persistence.complete(eq(runId), eq(context), any(), any(), any(), any(), any())).thenReturn(completed);

        StrategyRun result = service.generate(tripId);
        assertThat(result.getStatus()).isEqualTo(StrategyRunStatus.COMPLETED);
        verify(reasoner).reason(context, List.of());
        ArgumentCaptor<com.aifishing.strategy.domain.FishingStrategyProfile> profileCaptor =
                ArgumentCaptor.forClass(com.aifishing.strategy.domain.FishingStrategyProfile.class);
        verify(persistence).complete(eq(runId), eq(context), any(), profileCaptor.capture(), any(), any(), any());
        assertThat(profileCaptor.getValue().systemConfidence()).isEqualTo(0.41);
        assertThat(profileCaptor.getValue().dataLimitations())
                .anyMatch(item -> item.code().name().equals("STRUCTURE_NONE_AFTER_ANALYSIS"));
    }

    @Test
    void retriesOnceThenFailsValidation() {
        FishingContext context = StrategyFixtures.context(
                PipelineReadiness.READY, WeatherAvailability.FORECAST_AVAILABLE, true, 2, 0.8, 12.0);
        when(contextBuilder.build(any(), any())).thenReturn(context);
        when(contextBuilder.pipelineNotReady(context)).thenReturn(false);
        when(reasoner.reason(any(), any())).thenReturn(
                new FishingStrategyReasoner.StrategyReasonerResult(StrategyFixtures.validProfile(), null, null));
        when(validator.validate(any(), eq(context))).thenReturn(List.of("bad weight"));
        when(persistence.fail(eq(runId), eq(context), any(), any(), any(), any(), any())).thenReturn(failedRun());

        StrategyRun result = service.generate(tripId);
        assertThat(result.getStatus()).isEqualTo(StrategyRunStatus.FAILED);
        verify(reasoner).reason(context, List.of());
        verify(reasoner).reason(context, List.of("bad weight"));
    }

    private Trip trip() {
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setUserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        trip.setLakeId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        trip.setPrimaryTargetSpecies(FishSpecies.SMALLMOUTH_BASS);
        trip.setPlannedDate(LocalDate.of(2026, 9, 12));
        trip.setFishingStartTime(LocalTime.of(6, 0));
        trip.setFishingEndTime(LocalTime.of(15, 0));
        trip.setFishingMode(FishingMode.BOAT);
        trip.setStatus(TripStatus.DRAFT);
        return trip;
    }

    private StrategyRun failedRun() {
        StrategyRun run = new StrategyRun();
        run.setId(runId);
        run.setStatus(StrategyRunStatus.FAILED);
        return run;
    }
}
