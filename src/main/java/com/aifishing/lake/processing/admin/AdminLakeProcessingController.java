package com.aifishing.lake.processing.admin;

import com.aifishing.lake.ops.LakeOpsJobResponse;
import com.aifishing.lake.ops.LakeOpsJobService;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.service.LakeStructureExtractionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/lakes")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminLakeProcessingController {

    private final LakeStructureExtractionService extractionService;
    private final LakeOpsJobService lakeOpsJobService;

    public AdminLakeProcessingController(
            LakeStructureExtractionService extractionService,
            LakeOpsJobService lakeOpsJobService
    ) {
        this.extractionService = extractionService;
        this.lakeOpsJobService = lakeOpsJobService;
    }

    @PostMapping("/{lakeId}/process")
    public ResponseEntity<LakeOpsJobResponse> process(
            @PathVariable UUID lakeId,
            @RequestParam(defaultValue = "GIS") Pipeline pipeline
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(lakeOpsJobService.enqueueProcess(lakeId, pipeline));
    }

    @PostMapping("/{lakeId}/benchmark")
    public Map<String, Object> runBenchmark(@PathVariable UUID lakeId) {
        return extractionService.benchmark(lakeId);
    }

    @GetMapping("/{lakeId}/benchmark")
    public Map<String, Object> lastBenchmark(@PathVariable UUID lakeId) {
        return extractionService.lastBenchmark(lakeId);
    }

    @GetMapping("/benchmark-summary")
    public Map<String, Object> benchmarkSummary(@RequestParam("ids") String ids) {
        List<UUID> lakeIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .toList();
        return extractionService.benchmarkSummary(lakeIds);
    }

    @PostMapping("/{lakeId}/spatial-snapshots")
    public ResponseEntity<LakeOpsJobResponse> rebuildSpatialSnapshot(
            @PathVariable UUID lakeId,
            @RequestParam(defaultValue = "GIS") Pipeline pipeline,
            @RequestParam(required = false) String analysisVersion
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(lakeOpsJobService.enqueueSnapshot(lakeId, pipeline, analysisVersion));
    }

    @GetMapping("/{lakeId}/spatial-snapshots")
    public Map<String, Object> spatialSnapshots(@PathVariable UUID lakeId) {
        return extractionService.spatialSnapshots(lakeId);
    }

    @GetMapping("/{lakeId}/features")
    public LakeFeaturesResponse features(
            @PathVariable UUID lakeId,
            @RequestParam(required = false) Pipeline pipeline
    ) {
        return extractionService.features(lakeId, pipeline == null ? Pipeline.GIS : pipeline);
    }

    @GetMapping(value = "/{lakeId}/features.geojson", produces = {"application/geo+json", "application/json"})
    public Map<String, Object> featuresGeoJson(
            @PathVariable UUID lakeId,
            @RequestParam(required = false) Pipeline pipeline,
            @RequestParam(required = false) FeatureType type,
            @RequestParam(required = false) Double minConfidence,
            @RequestParam(required = false) String analysisVersion
    ) {
        return extractionService.featuresGeoJson(
                lakeId,
                pipeline == null ? Pipeline.GIS : pipeline,
                type,
                minConfidence,
                analysisVersion
        );
    }

    @GetMapping(value = "/{lakeId}/map.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> map(@PathVariable UUID lakeId) {
        byte[] png = extractionService.mapPng(lakeId);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }
}
