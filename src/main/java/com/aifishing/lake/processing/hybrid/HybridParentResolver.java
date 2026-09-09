package com.aifishing.lake.processing.hybrid;

import com.aifishing.common.exception.BadRequestException;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.dto.StructurePipelineAvailability;
import com.aifishing.lake.processing.service.StructurePipelineReadiness;
import com.aifishing.lake.processing.service.StructurePipelineReadinessService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HybridParentResolver {

    private final StructurePipelineReadinessService readinessService;

    public HybridParentResolver(StructurePipelineReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    public HybridParentPins pin(UUID lakeId) {
        StructurePipelineReadiness gis = readinessService.evaluate(lakeId, Pipeline.GIS);
        StructurePipelineReadiness vision = readinessService.evaluate(lakeId, Pipeline.VISION);
        requireReady(Pipeline.GIS, gis);
        requireReady(Pipeline.VISION, vision);
        LakeAnalysisRun gisRun = gis.run();
        LakeAnalysisRun visionRun = vision.run();
        if (gisRun == null || visionRun == null) {
            throw new BadRequestException("HYBRID_PARENT_INCOMPATIBLE", "GIS and VISION parent runs are required before Hybrid.");
        }
        if (gisRun.getSourceSnapshotId() == null
                || !gisRun.getSourceSnapshotId().equals(visionRun.getSourceSnapshotId())) {
            throw new BadRequestException(
                    "HYBRID_PARENT_INCOMPATIBLE",
                    "GIS and VISION runs were produced from incompatible canonical source snapshots.");
        }
        return new HybridParentPins(
                gisRun.getId(),
                gisRun.getAnalysisVersion(),
                visionRun.getId(),
                visionRun.getAnalysisVersion(),
                gisRun.getSourceSnapshotId()
        );
    }

    private void requireReady(Pipeline pipeline, StructurePipelineReadiness readiness) {
        if (readiness.availability() != StructurePipelineAvailability.READY) {
            throw new BadRequestException(
                    "HYBRID_PARENT_NOT_READY",
                    pipeline + " is " + readiness.availability() + " and cannot be used as a Hybrid parent.");
        }
    }
}
