package com.aifishing.fishingsession.admin;

import com.aifishing.fishingsession.dto.AdminFishingSessionResponse;
import com.aifishing.fishingsession.service.FishingSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/fishing-sessions")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminFishingSessionController {

    private final FishingSessionService fishingSessionService;

    public AdminFishingSessionController(FishingSessionService fishingSessionService) {
        this.fishingSessionService = fishingSessionService;
    }

    @GetMapping("/{sessionId}")
    public AdminFishingSessionResponse get(@PathVariable UUID sessionId) {
        return fishingSessionService.adminGet(sessionId);
    }
}
