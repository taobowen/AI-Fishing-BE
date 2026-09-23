package com.aifishing.webquota.api;

import com.aifishing.planning.api.GeneratePrefer;
import com.aifishing.planning.dto.GeneratePlanRequest;
import com.aifishing.planning.dto.GeneratePlanResponse;
import com.aifishing.planning.service.TripPlanningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/web/trips")
public class WebPlanController {

    private final TripPlanningService planningService;

    public WebPlanController(TripPlanningService planningService) {
        this.planningService = planningService;
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
}
