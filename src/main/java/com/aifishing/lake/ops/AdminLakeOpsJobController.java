package com.aifishing.lake.ops;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/lakes/jobs")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminLakeOpsJobController {

    private final LakeOpsJobService jobService;

    public AdminLakeOpsJobController(LakeOpsJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<LakeOpsJobResponse> get(@PathVariable UUID jobId) {
        return ResponseEntity.ok(jobService.get(jobId));
    }
}
