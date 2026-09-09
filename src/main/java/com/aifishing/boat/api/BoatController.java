package com.aifishing.boat.api;

import com.aifishing.boat.service.BoatService;
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
@RequestMapping("/api/v1/me/boats")
public class BoatController {

    private final BoatService boatService;

    public BoatController(BoatService boatService) {
        this.boatService = boatService;
    }

    @GetMapping
    public List<BoatResponse> list(@RequestParam(defaultValue = "false") boolean includeSystem) {
        return boatService.list(includeSystem);
    }

    @PostMapping("/web-default")
    public BoatResponse webDefault(@RequestParam(required = false) WebBoatPreset preset) {
        return boatService.webDefault(preset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoatResponse create(@Valid @RequestBody CreateBoatRequest request) {
        return boatService.create(request);
    }

    @GetMapping("/{id}")
    public BoatResponse get(@PathVariable UUID id) {
        return boatService.get(id);
    }

    @PatchMapping("/{id}")
    public BoatResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBoatRequest request) {
        return boatService.update(id, request);
    }

    @PostMapping("/{id}/resolve-capability")
    public BoatResponse resolveCapability(@PathVariable UUID id) {
        return boatService.resolveCapability(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        boatService.delete(id);
    }
}
