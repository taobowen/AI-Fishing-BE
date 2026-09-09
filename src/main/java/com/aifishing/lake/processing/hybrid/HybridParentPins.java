package com.aifishing.lake.processing.hybrid;

import java.util.UUID;

public record HybridParentPins(
        UUID gisParentRunId,
        String gisAnalysisVersion,
        UUID visionParentRunId,
        String visionAnalysisVersion,
        String sourceSnapshotId
) {
}
