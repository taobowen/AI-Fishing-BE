package com.aifishing.planning.api;

import com.aifishing.planning.dto.GeneratePlanRequest;
import com.aifishing.planning.dto.GeneratePlanResponse;
import com.aifishing.planning.dto.TripPlanMapDataResponse;
import com.aifishing.planning.dto.TripPlanResponse;
import com.aifishing.planning.service.TripPlanningService;
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

    public TripPlanController(TripPlanningService planningService) {
        this.planningService = planningService;
    }

    @PostMapping("/{tripId}/plan")
    public GeneratePlanResponse generate(
            @PathVariable UUID tripId,
            @RequestBody(required = false) GeneratePlanRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return planningService.generate(tripId, request, idempotencyKey);
    }

    @GetMapping("/{tripId}/plan")
    public TripPlanResponse current(@PathVariable UUID tripId) {
        return planningService.current(tripId);
    }

    @GetMapping("/{tripId}/plans")
    public List<TripPlanResponse> history(@PathVariable UUID tripId) {
        return planningService.history(tripId);
    }

    @GetMapping("/{tripId}/plan/map-data")
    public TripPlanMapDataResponse mapData(@PathVariable UUID tripId) {
        return planningService.mapData(tripId);
    }
}
