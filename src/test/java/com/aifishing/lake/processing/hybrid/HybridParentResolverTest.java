package com.aifishing.lake.processing.hybrid;

import com.aifishing.common.exception.BadRequestException;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.dto.StructurePipelineAvailability;
import com.aifishing.lake.processing.service.StructurePipelineReadiness;
import com.aifishing.lake.processing.service.StructurePipelineReadinessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridParentResolverTest {

    @Mock
    StructurePipelineReadinessService readinessService;

    @InjectMocks
    HybridParentResolver resolver;

    @Test
    void refusesParentsFromIncompatibleSourceSnapshots() {
        UUID lakeId = UUID.randomUUID();
        LakeAnalysisRun gis = run(Pipeline.GIS, "gis-v", "src-a");
        LakeAnalysisRun vision = run(Pipeline.VISION, "vis-v", "src-b");
        when(readinessService.evaluate(lakeId, Pipeline.GIS)).thenReturn(StructurePipelineReadiness.of(
                Pipeline.GIS, StructurePipelineAvailability.READY, "gis-v", 2, gis));
        when(readinessService.evaluate(lakeId, Pipeline.VISION)).thenReturn(StructurePipelineReadiness.of(
                Pipeline.VISION, StructurePipelineAvailability.READY, "vis-v", 2, vision));

        assertThatThrownBy(() -> resolver.pin(lakeId))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getCode())
                .isEqualTo("HYBRID_PARENT_INCOMPATIBLE");
    }

    @Test
    void pinsMatchingParents() {
        UUID lakeId = UUID.randomUUID();
        LakeAnalysisRun gis = run(Pipeline.GIS, "gis-v", "src-same");
        LakeAnalysisRun vision = run(Pipeline.VISION, "vis-v", "src-same");
        when(readinessService.evaluate(lakeId, Pipeline.GIS)).thenReturn(StructurePipelineReadiness.of(
                Pipeline.GIS, StructurePipelineAvailability.READY, "gis-v", 2, gis));
        when(readinessService.evaluate(lakeId, Pipeline.VISION)).thenReturn(StructurePipelineReadiness.of(
                Pipeline.VISION, StructurePipelineAvailability.READY, "vis-v", 2, vision));

        HybridParentPins pins = resolver.pin(lakeId);
        assertThat(pins.gisParentRunId()).isEqualTo(gis.getId());
        assertThat(pins.gisAnalysisVersion()).isEqualTo("gis-v");
        assertThat(pins.visionParentRunId()).isEqualTo(vision.getId());
        assertThat(pins.visionAnalysisVersion()).isEqualTo("vis-v");
        assertThat(pins.sourceSnapshotId()).isEqualTo("src-same");
    }

    private LakeAnalysisRun run(Pipeline pipeline, String version, String fingerprint) {
        LakeAnalysisRun run = new LakeAnalysisRun();
        run.setId(UUID.randomUUID());
        run.setPipeline(pipeline);
        run.setAnalysisVersion(version);
        run.setSourceSnapshotId(fingerprint);
        return run;
    }
}
