package com.aifishing.feedback.admin;

import com.aifishing.feedback.performance.EmpiricalPerformanceService;
import com.aifishing.feedback.performance.dto.LakeEmpiricalSummaryResponse;
import com.aifishing.feedback.performance.dto.SessionPerformanceResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminFeedbackController {

    private final EmpiricalPerformanceService performanceService;

    public AdminFeedbackController(EmpiricalPerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @GetMapping("/fishing-sessions/{sessionId}/performance")
    public SessionPerformanceResponse sessionPerformance(@PathVariable UUID sessionId) {
        return performanceService.get(sessionId, null);
    }

    @GetMapping("/lakes/{lakeId}/empirical-summary")
    public LakeEmpiricalSummaryResponse lakeSummary(@PathVariable UUID lakeId) {
        return performanceService.lakeSummary(lakeId);
    }
}
