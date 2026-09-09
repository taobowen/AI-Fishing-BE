package com.aifishing.planning.admin;

import com.aifishing.planning.dto.PlanningRunResponse;
import com.aifishing.planning.dto.PlanningRunSummaryResponse;
import com.aifishing.planning.service.TripPlanningService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/trips")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminTripPlanningController {

    private final TripPlanningService planningService;

    public AdminTripPlanningController(TripPlanningService planningService) {
        this.planningService = planningService;
    }

    @GetMapping("/{tripId}/planning-runs")
    public List<PlanningRunSummaryResponse> runs(@PathVariable UUID tripId) {
        return planningService.runs(tripId);
    }

    @GetMapping("/{tripId}/planning-runs/{runId}")
    public PlanningRunResponse run(@PathVariable UUID tripId, @PathVariable UUID runId) {
        return planningService.run(tripId, runId);
    }

    @GetMapping("/{tripId}/plan-diagnostics")
    public Map<String, Object> diagnostics(@PathVariable UUID tripId) {
        return planningService.diagnostics(tripId);
    }
}
