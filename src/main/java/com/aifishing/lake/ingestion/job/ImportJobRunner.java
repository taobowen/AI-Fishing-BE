package com.aifishing.lake.ingestion.job;

import com.aifishing.lake.ingestion.admin.LakeImportSummaryResponse;
import com.aifishing.lake.ingestion.dto.DatasetType;

import java.util.UUID;

public interface ImportJobRunner {

    LakeImportSummaryResponse run(UUID lakeId);

    LakeImportSummaryResponse run(UUID lakeId, DatasetType dataset);
}
