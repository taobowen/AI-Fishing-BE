package com.aifishing.fishingsession.api;

import com.aifishing.fishingsession.dto.ClientEventRequest;
import com.aifishing.fishingsession.dto.FishingSessionResponse;
import com.aifishing.fishingsession.dto.LocationBatchRequest;
import com.aifishing.fishingsession.dto.NavigationResponse;
import com.aifishing.fishingsession.dto.SessionResultsResponse;
import com.aifishing.fishingsession.dto.SessionTrackResponse;
import com.aifishing.fishingsession.dto.StartFishingSessionRequest;
import com.aifishing.fishingsession.service.FishingSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class FishingSessionController {

    private final FishingSessionService fishingSessionService;

    public FishingSessionController(FishingSessionService fishingSessionService) {
        this.fishingSessionService = fishingSessionService;
    }

    @PostMapping("/api/v1/trips/{tripId}/fishing-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public FishingSessionResponse start(
            @PathVariable UUID tripId,
            @RequestBody(required = false) StartFishingSessionRequest request
    ) {
        return fishingSessionService.start(tripId, request);
    }

    @GetMapping("/api/v1/fishing-sessions/current")
    public ResponseEntity<FishingSessionResponse> current() {
        return fishingSessionService.currentUnfinished()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/v1/fishing-sessions/{sessionId}")
    public FishingSessionResponse get(@PathVariable UUID sessionId) {
        return fishingSessionService.get(sessionId);
    }

    @GetMapping("/api/v1/fishing-sessions/{sessionId}/results")
    public SessionResultsResponse results(@PathVariable UUID sessionId) {
        return fishingSessionService.results(sessionId);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/locations")
    public FishingSessionResponse locations(
            @PathVariable UUID sessionId,
            @Valid @RequestBody LocationBatchRequest request
    ) {
        return fishingSessionService.ingestLocations(sessionId, request);
    }

    @GetMapping("/api/v1/fishing-sessions/{sessionId}/navigation")
    public NavigationResponse navigation(@PathVariable UUID sessionId) {
        return fishingSessionService.navigation(sessionId);
    }

    @GetMapping("/api/v1/fishing-sessions/{sessionId}/track")
    public SessionTrackResponse track(@PathVariable UUID sessionId) {
        return fishingSessionService.track(sessionId);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/pause")
    public FishingSessionResponse pause(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ClientEventRequest request
    ) {
        return fishingSessionService.pause(sessionId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/resume")
    public FishingSessionResponse resume(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ClientEventRequest request
    ) {
        return fishingSessionService.resume(sessionId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/end")
    public FishingSessionResponse end(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ClientEventRequest request
    ) {
        return fishingSessionService.end(sessionId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/ad-hoc-fishing/start")
    public FishingSessionResponse startAdHoc(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ClientEventRequest request
    ) {
        return fishingSessionService.startAdHocFishing(sessionId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/ad-hoc-fishing/end")
    public FishingSessionResponse endAdHoc(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ClientEventRequest request
    ) {
        return fishingSessionService.endAdHocFishing(sessionId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/stationary-prompt/dismiss")
    public FishingSessionResponse dismissStationaryPrompt(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ClientEventRequest request
    ) {
        return fishingSessionService.dismissStationaryPrompt(sessionId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/waypoints/{waypointId}/arrive")
    public FishingSessionResponse arrive(
            @PathVariable UUID sessionId,
            @PathVariable UUID waypointId,
            @Valid @RequestBody ClientEventRequest request
    ) {
        return fishingSessionService.arrive(sessionId, waypointId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/waypoints/{waypointId}/skip")
    public FishingSessionResponse skip(
            @PathVariable UUID sessionId,
            @PathVariable UUID waypointId,
            @Valid @RequestBody ClientEventRequest request
    ) {
        return fishingSessionService.skip(sessionId, waypointId, request);
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/waypoints/{waypointId}/complete")
    public FishingSessionResponse complete(
            @PathVariable UUID sessionId,
            @PathVariable UUID waypointId,
            @Valid @RequestBody ClientEventRequest request
    ) {
        return fishingSessionService.complete(sessionId, waypointId, request);
    }
}
