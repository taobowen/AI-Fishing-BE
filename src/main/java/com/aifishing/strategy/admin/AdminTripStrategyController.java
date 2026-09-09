package com.aifishing.strategy.admin;

import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.dto.StrategyRunResponse;
import com.aifishing.strategy.dto.StrategyRunSummaryResponse;
import com.aifishing.strategy.service.FishingStrategyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/trips")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminTripStrategyController {

    private final FishingStrategyService strategyService;
    private final ObjectMapper objectMapper;

    public AdminTripStrategyController(FishingStrategyService strategyService, ObjectMapper objectMapper) {
        this.strategyService = strategyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{tripId}/strategy")
    public StrategyRunResponse generate(@PathVariable UUID tripId) {
        return toResponse(strategyService.generate(tripId));
    }

    @GetMapping("/{tripId}/strategy")
    public StrategyRunResponse latest(@PathVariable UUID tripId) {
        return toResponse(strategyService.latestCompleted(tripId));
    }

    @GetMapping("/{tripId}/strategy-runs")
    public List<StrategyRunSummaryResponse> runs(@PathVariable UUID tripId) {
        return strategyService.runs(tripId).stream().map(this::toSummary).toList();
    }

    @GetMapping("/{tripId}/strategy/context")
    public FishingContext context(@PathVariable UUID tripId) {
        return strategyService.context(tripId);
    }

    private StrategyRunResponse toResponse(StrategyRun run) {
        FishingStrategyProfile profile = run.getStrategyProfile() == null
                ? null
                : objectMapper.convertValue(run.getStrategyProfile(), FishingStrategyProfile.class);
        return new StrategyRunResponse(
                run.getId(),
                run.getTripId(),
                run.getStatus(),
                run.getModelId(),
                run.getPromptVersion(),
                run.getFeaturePipeline(),
                run.getStartedAt(),
                run.getCompletedAt(),
                profile,
                run.getSourceMetadata(),
                run.getUsageMetadata(),
                run.getErrorMessage()
        );
    }

    private StrategyRunSummaryResponse toSummary(StrategyRun run) {
        return new StrategyRunSummaryResponse(
                run.getId(),
                run.getTripId(),
                run.getStatus(),
                run.getModelId(),
                run.getPromptVersion(),
                run.getFeaturePipeline(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getErrorMessage()
        );
    }
}
