package com.aifishing.guidance.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class SessionGuidanceSignalController {

    private final GuidanceService guidanceService;

    public SessionGuidanceSignalController(GuidanceService guidanceService) {
        this.guidanceService = guidanceService;
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/bites")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bite(@PathVariable UUID sessionId, @Valid @RequestBody BiteRequest request) {
        guidanceService.recordBite(sessionId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/fish-on")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fishOn(@PathVariable UUID sessionId, @Valid @RequestBody FishOnRequest request) {
        guidanceService.recordFishOn(sessionId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/lure-events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lure(@PathVariable UUID sessionId, @Valid @RequestBody LureEventRequest request) {
        guidanceService.recordLure(sessionId, request);
    }
}
