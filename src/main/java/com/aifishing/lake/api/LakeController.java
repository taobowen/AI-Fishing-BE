package com.aifishing.lake.api;

import com.aifishing.lake.service.LakeService;
import com.aifishing.launch.BoatLaunchService;
import com.aifishing.launch.api.BoatLaunchResponse;
import com.aifishing.launch.api.CustomLaunchPreviewRequest;
import com.aifishing.launch.api.CustomLaunchPreviewResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lakes")
public class LakeController {

    private final LakeService lakeService;
    private final BoatLaunchService boatLaunchService;

    public LakeController(LakeService lakeService, BoatLaunchService boatLaunchService) {
        this.lakeService = lakeService;
        this.boatLaunchService = boatLaunchService;
    }

    @GetMapping
    public List<LakeSummaryResponse> search(@RequestParam(required = false) String query) {
        return lakeService.search(query);
    }

    @GetMapping("/{id}")
    public LakeResponse get(@PathVariable UUID id) {
        return lakeService.get(id);
    }

    @GetMapping("/{id}/contours")
    public List<BathymetryContourDto> contours(@PathVariable UUID id) {
        return lakeService.contours(id);
    }

    @GetMapping("/{id}/planning-capabilities")
    public LakePlanningCapabilitiesResponse planningCapabilities(@PathVariable UUID id) {
        return lakeService.planningCapabilities(id);
    }

    @GetMapping("/{id}/boat-launches")
    public List<BoatLaunchResponse> boatLaunches(@PathVariable UUID id) {
        return boatLaunchService.list(id);
    }

    @PostMapping("/{id}/boat-launches/custom-preview")
    public CustomLaunchPreviewResponse customPreview(
            @PathVariable UUID id,
            @Valid @RequestBody CustomLaunchPreviewRequest request
    ) {
        return boatLaunchService.preview(id, request);
    }
}
