package com.aifishing.guidance.api;

import com.aifishing.guidance.contracts.GuidanceCurrentResponse;
import com.aifishing.guidance.contracts.GuidanceDecisionRequest;
import com.aifishing.guidance.contracts.GuidanceFeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class GuidanceController {

    private final GuidanceService guidanceService;

    public GuidanceController(GuidanceService guidanceService) {
        this.guidanceService = guidanceService;
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/guidance/decisions")
    public GuidanceCurrentResponse createDecision(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) GuidanceDecisionRequest request
    ) {
        return guidanceService.requestDecision(sessionId, request);
    }

    @GetMapping("/api/v1/fishing-sessions/{sessionId}/guidance/current")
    public GuidanceCurrentResponse current(@PathVariable UUID sessionId) {
        return guidanceService.current(sessionId);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/guidance/decisions/{decisionId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void feedback(
            @PathVariable UUID sessionId,
            @PathVariable UUID decisionId,
            @Valid @RequestBody GuidanceFeedbackRequest request
    ) {
        guidanceService.submitFeedback(sessionId, decisionId, request);
    }
}
