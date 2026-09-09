package com.aifishing.webquota.api;

import com.aifishing.webquota.dto.UserPlanSummaryResponse;
import com.aifishing.webquota.dto.WebPlanEntitlementResponse;
import com.aifishing.webquota.service.UserPlanQueryService;
import com.aifishing.webquota.service.WebPlanQuotaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me")
public class WebEntitlementController {

    private final WebPlanQuotaService quotaService;
    private final UserPlanQueryService planQueryService;

    public WebEntitlementController(WebPlanQuotaService quotaService, UserPlanQueryService planQueryService) {
        this.quotaService = quotaService;
        this.planQueryService = planQueryService;
    }

    @GetMapping("/web-plan-entitlement")
    public WebPlanEntitlementResponse entitlement() {
        return quotaService.current();
    }

    @GetMapping("/plans")
    public List<UserPlanSummaryResponse> plans() {
        return planQueryService.listMine();
    }
}
