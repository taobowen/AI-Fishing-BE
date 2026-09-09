package com.aifishing.lake.ingestion.job;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.admin.DatasetStatusResponse;
import com.aifishing.lake.ingestion.admin.LakeDataSummaryResponse;
import com.aifishing.lake.ingestion.admin.LakeImportSummaryResponse;
import com.aifishing.lake.ingestion.admin.PaginationReportAssembler;
import com.aifishing.lake.ingestion.domain.LakeDatasetStatus;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.processing.admin.AnalysisQualityAssembler;
import com.aifishing.lake.repo.LakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LakeDataIngestionService {

    private final ImportJobRunner importJobRunner;
    private final LakeRepository lakeRepository;
    private final LakeDatasetStatusRepository datasetStatusRepository;
    private final AnalysisQualityAssembler analysisQualityAssembler;
    private final PaginationReportAssembler paginationReportAssembler;

    public LakeDataIngestionService(
            ImportJobRunner importJobRunner,
            LakeRepository lakeRepository,
            LakeDatasetStatusRepository datasetStatusRepository,
            AnalysisQualityAssembler analysisQualityAssembler,
            PaginationReportAssembler paginationReportAssembler
    ) {
        this.importJobRunner = importJobRunner;
        this.lakeRepository = lakeRepository;
        this.datasetStatusRepository = datasetStatusRepository;
        this.analysisQualityAssembler = analysisQualityAssembler;
        this.paginationReportAssembler = paginationReportAssembler;
    }

    public LakeImportSummaryResponse importLake(UUID lakeId) {
        return importJobRunner.run(lakeId);
    }

    public LakeImportSummaryResponse importLake(UUID lakeId, DatasetType dataset) {
        return importJobRunner.run(lakeId, dataset);
    }

    @Transactional(readOnly = true)
    public List<DatasetStatusResponse> datasets(UUID lakeId) {
        requireLake(lakeId);
        return datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lakeId).stream()
                .map(this::toDataset)
                .toList();
    }

    @Transactional(readOnly = true)
    public LakeDataSummaryResponse summary(UUID lakeId) {
        Lake lake = requireLake(lakeId);
        return toSummary(lake, datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lakeId));
    }

    @Transactional(readOnly = true)
    public List<LakeDataSummaryResponse> summaries(List<UUID> ids) {
        return ids.stream().map(this::summary).toList();
    }

    private Lake requireLake(UUID lakeId) {
        return lakeRepository.findById(lakeId)
                .orElseThrow(() -> new NotFoundException("Lake not found"));
    }

    private LakeDataSummaryResponse toSummary(Lake lake, List<LakeDatasetStatus> statuses) {
        List<DatasetStatusResponse> datasets = statuses.stream()
                .map(this::toDataset)
                .toList();
        return new LakeDataSummaryResponse(
                lake.getId(),
                lake.getName(),
                lake.getOgfId(),
                lake.getOfficialName(),
                lake.getOgfId() != null,
                count(statuses, DatasetStatusCode.AVAILABLE),
                count(statuses, DatasetStatusCode.PARTIAL),
                count(statuses, DatasetStatusCode.NOT_AVAILABLE),
                count(statuses, DatasetStatusCode.FAILED),
                count(statuses, DatasetStatusCode.NOT_CHECKED) + count(statuses, DatasetStatusCode.IMPORTING),
                datasets,
                analysisQualityAssembler.quality(lake)
        );
    }

    private DatasetStatusResponse toDataset(LakeDatasetStatus status) {
        return DatasetStatusResponse.from(
                status,
                paginationReportAssembler.forDataset(status.getLakeId(), status.getDatasetType())
        );
    }

    private int count(List<LakeDatasetStatus> statuses, DatasetStatusCode code) {
        return (int) statuses.stream().filter(status -> status.getStatus() == code).count();
    }
}
