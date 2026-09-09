package com.aifishing.lake.ingestion.admin;

import com.aifishing.lake.processing.admin.LakeAnalysisQuality;

import java.util.List;
import java.util.UUID;

public record LakeDataSummaryResponse(
        UUID lakeId,
        String lakeName,
        Long ogfId,
        String officialName,
        boolean identityResolved,
        int availableDatasets,
        int partialDatasets,
        int notAvailableDatasets,
        int failedDatasets,
        int notCheckedDatasets,
        List<DatasetStatusResponse> datasets,
        LakeAnalysisQuality analysis
) {
}
