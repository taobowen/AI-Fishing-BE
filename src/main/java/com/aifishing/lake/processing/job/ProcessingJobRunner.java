package com.aifishing.lake.processing.job;

import com.aifishing.lake.processing.admin.LakeProcessSummaryResponse;

import java.util.UUID;

public interface ProcessingJobRunner {

    LakeProcessSummaryResponse run(UUID lakeId);
}
