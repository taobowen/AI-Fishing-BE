package com.aifishing.planning.api;

import com.aifishing.planning.dto.GeneratePlanRequest;
import com.aifishing.planning.dto.GeneratePlanResponse;
import com.aifishing.planning.dto.PlanningRunResponse;
import com.aifishing.planning.dto.TripPlanMapDataResponse;
import com.aifishing.planning.dto.TripPlanResponse;
import com.aifishing.planning.service.TripPlanningService;
import com.aifishing.planning.tactics.TacticsEnrichmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
public class TripPlanController {

    private final TripPlanningService planningService;
    private final TacticsEnrichmentService tacticsEnrichmentService;

    public TripPlanController(
            TripPlanningService planningService,
            TacticsEnrichmentService tacticsEnrichmentService
    ) {
        this.planningService = planningService;
        this.tacticsEnrichmentService = tacticsEnrichmentService;
    }

    @PostMapping("/{tripId}/plan")
    public ResponseEntity<GeneratePlanResponse> generate(
            @PathVariable UUID tripId,
            @RequestBody(required = false) GeneratePlanRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Prefer", required = false) String prefer
    ) {
        return planningService.generateMaybeAsync(tripId, request, idempotencyKey, GeneratePrefer.respondAsync(prefer));
    }

    @GetMapping("/{tripId}/plan")
    public TripPlanResponse current(@PathVariable UUID tripId) {
        return planningService.current(tripId);
    }

    @GetMapping("/{tripId}/planning-runs/{runId}")
    public PlanningRunResponse planningRun(@PathVariable UUID tripId, @PathVariable UUID runId) {
        return planningService.ownedRun(tripId, runId);
    }

    @GetMapping("/{tripId}/plans")
    public List<TripPlanResponse> history(@PathVariable UUID tripId) {
        return planningService.history(tripId);
    }

    @GetMapping("/{tripId}/plan/map-data")
    public TripPlanMapDataResponse mapData(@PathVariable UUID tripId) {
        return planningService.mapData(tripId);
    }

    @PostMapping("/{tripId}/plans/{planId}/tactics")
    public TripPlanResponse enrichTactics(@PathVariable UUID tripId, @PathVariable UUID planId) {
        return tacticsEnrichmentService.enrich(tripId, planId);
    }
}
