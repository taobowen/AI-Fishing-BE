package com.aifishing.analytics.api;

import com.aifishing.analytics.service.ProductAnalyticsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final ProductAnalyticsService productAnalyticsService;

    public AnalyticsController(ProductAnalyticsService productAnalyticsService) {
        this.productAnalyticsService = productAnalyticsService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackAnalyticsEventResponse track(@Valid @RequestBody TrackAnalyticsEventRequest request) {
        return productAnalyticsService.track(request);
    }
}
