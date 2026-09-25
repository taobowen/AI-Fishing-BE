package com.aifishing.trip.api;

import com.aifishing.common.enums.TripStatus;
import com.aifishing.trip.service.PastTripQueryService;
import com.aifishing.trip.service.RequiredPointReachabilityService;
import com.aifishing.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;
    private final PastTripQueryService pastTripQueryService;
    private final RequiredPointReachabilityService requiredPointReachabilityService;

    public TripController(
            TripService tripService,
            PastTripQueryService pastTripQueryService,
            RequiredPointReachabilityService requiredPointReachabilityService
    ) {
        this.tripService = tripService;
        this.pastTripQueryService = pastTripQueryService;
        this.requiredPointReachabilityService = requiredPointReachabilityService;
    }

    @GetMapping
    public List<TripResponse> list(
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return tripService.list(status, from, to);
    }

    @GetMapping("/past")
    public List<PastTripResponse> past(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
    ) {
        return pastTripQueryService.listPast(month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@Valid @RequestBody CreateTripRequest request) {
        return tripService.create(request);
    }

    @PostMapping("/required-point-reachability")
    public RequiredPointReachabilityResponse requiredPointReachability(
            @Valid @RequestBody RequiredPointReachabilityRequest request
    ) {
        return requiredPointReachabilityService.estimate(request);
    }

    @GetMapping("/{id}")
    public TripResponse get(@PathVariable UUID id) {
        return tripService.get(id);
    }

    @PatchMapping("/{id}")
    public TripResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTripRequest request) {
        return tripService.update(id, request);
    }
}
