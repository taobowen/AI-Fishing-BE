package com.aifishing.fishingtemplate.api;

import com.aifishing.fishingtemplate.service.FishingTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fishing-templates")
public class FishingTemplateController {

    private final FishingTemplateService fishingTemplateService;

    public FishingTemplateController(FishingTemplateService fishingTemplateService) {
        this.fishingTemplateService = fishingTemplateService;
    }

    @GetMapping
    public List<FishingTemplateResponse> list(@RequestParam UUID lakeId) {
        return fishingTemplateService.list(lakeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FishingTemplateResponse create(@Valid @RequestBody CreateFishingTemplateRequest request) {
        return fishingTemplateService.create(request);
    }

    @GetMapping("/{id}")
    public FishingTemplateResponse get(@PathVariable UUID id) {
        return fishingTemplateService.get(id);
    }

    @PatchMapping("/{id}")
    public FishingTemplateResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFishingTemplateRequest request
    ) {
        return fishingTemplateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        fishingTemplateService.delete(id);
    }
}
