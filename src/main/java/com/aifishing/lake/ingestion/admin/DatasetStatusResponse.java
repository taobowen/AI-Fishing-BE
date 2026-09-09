package com.aifishing.lake.ingestion.admin;

import com.aifishing.lake.ingestion.domain.LakeDatasetStatus;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;

import java.time.Instant;

public record DatasetStatusResponse(
        DatasetType datasetType,
        String provider,
        DatasetStatusCode status,
        Integer recordCount,
        Instant lastAttemptedAt,
        Instant lastSuccessfulImportAt,
        String sourceReference,
        String errorMessage,
        Integer pageCount,
        Integer rawRecordCount,
        Boolean transferLimitObserved,
        Boolean paginationComplete,
        String paginationWarning,
        String storageUriScheme
) {
    public static DatasetStatusResponse from(LakeDatasetStatus status) {
        return from(status, PaginationReport.empty());
    }

    public static DatasetStatusResponse from(LakeDatasetStatus status, PaginationReport pagination) {
        PaginationReport report = pagination == null ? PaginationReport.empty() : pagination;
        return new DatasetStatusResponse(
                status.getDatasetType(),
                status.getProvider(),
                status.getStatus(),
                status.getRecordCount(),
                status.getLastAttemptedAt(),
                status.getLastSuccessfulImportAt(),
                status.getSourceReference(),
                status.getErrorMessage(),
                report.pageCount(),
                report.rawRecordCount(),
                report.transferLimitObserved(),
                report.paginationComplete(),
                report.paginationWarning(),
                report.storageUriScheme()
        );
    }
}
