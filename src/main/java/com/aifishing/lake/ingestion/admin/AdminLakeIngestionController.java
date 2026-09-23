package com.aifishing.lake.ingestion.admin;

import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.job.LakeDataIngestionService;
import com.aifishing.lake.ops.LakeOpsJobResponse;
import com.aifishing.lake.ops.LakeOpsJobService;
import com.aifishing.seed.ValidationCatalogResponse;
import com.aifishing.seed.ValidationCatalogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/lakes")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminLakeIngestionController {

    private final LakeDataIngestionService ingestionService;
    private final ValidationCatalogService catalogService;
    private final LakeBootstrapValidationService bootstrapValidationService;
    private final LakeOpsJobService lakeOpsJobService;

    public AdminLakeIngestionController(
            LakeDataIngestionService ingestionService,
            ValidationCatalogService catalogService,
            LakeBootstrapValidationService bootstrapValidationService,
            LakeOpsJobService lakeOpsJobService
    ) {
        this.ingestionService = ingestionService;
        this.catalogService = catalogService;
        this.bootstrapValidationService = bootstrapValidationService;
        this.lakeOpsJobService = lakeOpsJobService;
    }

    @PostMapping("/validation-catalog")
    public ValidationCatalogResponse ensureValidationCatalog() {
        List<ValidationCatalogResponse.LakeRef> refs = catalogService.ensureCatalog().stream()
                .map(result -> new ValidationCatalogResponse.LakeRef(
                        result.lake().getId(),
                        result.lake().getName(),
                        result.created(),
                        result.lake().getOgfId() != null
                ))
                .toList();
        return new ValidationCatalogResponse(refs);
    }

    @GetMapping("/{lakeId}/bootstrap-validation")
    public LakeBootstrapValidationResponse bootstrapValidation(@PathVariable UUID lakeId) {
        return bootstrapValidationService.validate(lakeId);
    }

    @PostMapping("/{lakeId}/import")
    public ResponseEntity<LakeOpsJobResponse> importLake(
            @PathVariable UUID lakeId,
            @RequestParam(required = false) DatasetType dataset
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(lakeOpsJobService.enqueueImport(lakeId, dataset));
    }

    @GetMapping("/{lakeId}/datasets")
    public List<DatasetStatusResponse> datasets(@PathVariable UUID lakeId) {
        return ingestionService.datasets(lakeId);
    }

    @GetMapping("/{lakeId}/data-summary")
    public LakeDataSummaryResponse dataSummary(@PathVariable UUID lakeId) {
        return ingestionService.summary(lakeId);
    }

    @GetMapping("/data-summary")
    public List<LakeDataSummaryResponse> dataSummaries(@RequestParam("ids") String ids) {
        List<UUID> lakeIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .toList();
        return ingestionService.summaries(lakeIds);
    }
}
