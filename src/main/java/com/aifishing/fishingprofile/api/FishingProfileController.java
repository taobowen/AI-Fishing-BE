package com.aifishing.fishingprofile.api;

import com.aifishing.fishingprofile.service.FishingProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/fishing-profile")
public class FishingProfileController {

    private final FishingProfileService fishingProfileService;

    public FishingProfileController(FishingProfileService fishingProfileService) {
        this.fishingProfileService = fishingProfileService;
    }

    @GetMapping
    public FishingProfileResponse get() {
        return fishingProfileService.getCurrent();
    }

    @PutMapping
    public FishingProfileResponse upsert(@Valid @RequestBody UpsertFishingProfileRequest request) {
        return fishingProfileService.upsert(request);
    }
}
