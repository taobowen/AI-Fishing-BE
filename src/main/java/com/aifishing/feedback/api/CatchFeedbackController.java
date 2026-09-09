package com.aifishing.feedback.api;

import com.aifishing.auth.CurrentUser;
import com.aifishing.feedback.catchlog.dto.CatchEventResponse;
import com.aifishing.feedback.catchlog.dto.CatchPhotoResponse;
import com.aifishing.feedback.catchlog.dto.CreateCatchRequest;
import com.aifishing.feedback.catchlog.dto.PhotoUploadRequest;
import com.aifishing.feedback.catchlog.dto.PhotoUploadResponse;
import com.aifishing.feedback.catchlog.dto.UpdateCatchRequest;
import com.aifishing.feedback.catchlog.service.CatchEventService;
import com.aifishing.feedback.catchlog.service.CatchPhotoService;
import com.aifishing.feedback.performance.EmpiricalPerformanceService;
import com.aifishing.feedback.performance.dto.SessionPerformanceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CatchFeedbackController {

    private final CatchEventService catchEventService;
    private final CatchPhotoService catchPhotoService;
    private final EmpiricalPerformanceService performanceService;
    private final CurrentUser currentUser;

    public CatchFeedbackController(
            CatchEventService catchEventService,
            CatchPhotoService catchPhotoService,
            EmpiricalPerformanceService performanceService,
            CurrentUser currentUser
    ) {
        this.catchEventService = catchEventService;
        this.catchPhotoService = catchPhotoService;
        this.performanceService = performanceService;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/v1/fishing-sessions/{sessionId}/catches")
    @ResponseStatus(HttpStatus.CREATED)
    public CatchEventResponse create(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CreateCatchRequest request
    ) {
        return catchEventService.create(sessionId, request);
    }

    @GetMapping("/api/v1/fishing-sessions/{sessionId}/catches")
    public List<CatchEventResponse> list(@PathVariable UUID sessionId) {
        return catchEventService.list(sessionId);
    }

    @GetMapping("/api/v1/fishing-sessions/{sessionId}/performance")
    public SessionPerformanceResponse performance(@PathVariable UUID sessionId) {
        return performanceService.get(sessionId, currentUser.id());
    }

    @GetMapping("/api/v1/catches/{catchId}")
    public CatchEventResponse get(@PathVariable UUID catchId) {
        return catchEventService.get(catchId);
    }

    @PatchMapping("/api/v1/catches/{catchId}")
    public CatchEventResponse update(
            @PathVariable UUID catchId,
            @Valid @RequestBody UpdateCatchRequest request
    ) {
        return catchEventService.update(catchId, request);
    }

    @PostMapping("/api/v1/catches/{catchId}/void")
    public CatchEventResponse voidCatch(@PathVariable UUID catchId) {
        return catchEventService.voidCatch(catchId);
    }

    @PostMapping("/api/v1/catches/{catchId}/photos/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoUploadResponse uploadPhoto(
            @PathVariable UUID catchId,
            @Valid @RequestBody PhotoUploadRequest request
    ) {
        return catchPhotoService.startUpload(catchId, request);
    }

    @PostMapping("/api/v1/catches/{catchId}/photos/{photoId}/complete")
    public CatchPhotoResponse completePhoto(@PathVariable UUID catchId, @PathVariable UUID photoId) {
        return catchPhotoService.complete(catchId, photoId);
    }

    @GetMapping("/api/v1/catches/{catchId}/photos")
    public List<CatchPhotoResponse> listPhotos(@PathVariable UUID catchId) {
        return catchPhotoService.list(catchId);
    }

    @DeleteMapping("/api/v1/catches/{catchId}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable UUID catchId, @PathVariable UUID photoId) {
        catchPhotoService.delete(catchId, photoId);
    }
}
