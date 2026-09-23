package com.aifishing.planning.api;

import com.aifishing.planning.dto.WeatherPreviewRequest;
import com.aifishing.planning.dto.WeatherPreviewResponse;
import com.aifishing.planning.service.WeatherPreviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/planning")
public class PlanningWeatherPreviewController {

    private final WeatherPreviewService weatherPreviewService;

    public PlanningWeatherPreviewController(WeatherPreviewService weatherPreviewService) {
        this.weatherPreviewService = weatherPreviewService;
    }

    @PostMapping("/weather-preview")
    public WeatherPreviewResponse preview(@Valid @RequestBody WeatherPreviewRequest request) {
        return weatherPreviewService.preview(request);
    }
}
