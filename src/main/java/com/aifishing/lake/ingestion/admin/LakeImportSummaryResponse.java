package com.aifishing.lake.ingestion.admin;

import java.util.List;
import java.util.UUID;

public record LakeImportSummaryResponse(
        UUID lakeId,
        String lakeName,
        boolean identityResolved,
        String identityError,
        Long ogfId,
        String officialName,
        List<DatasetStatusResponse> datasets
) {
}
