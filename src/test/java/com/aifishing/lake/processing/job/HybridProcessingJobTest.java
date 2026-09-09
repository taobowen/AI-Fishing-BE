package com.aifishing.lake.processing.job;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.admin.LakeProcessSummaryResponse;
import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.hybrid.HybridMergeService;
import com.aifishing.lake.processing.hybrid.HybridParentPins;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.service.FeatureReplaceService;
import com.aifishing.lake.processing.service.FeatureStatusService;
import com.aifishing.lake.repo.LakeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridProcessingJobTest {

    @Mock
    LakeRepository lakeRepository;
    @Mock
    LakeFeatureRepository featureRepository;
    @Mock
    AnalysisContextFactory contextFactory;
    @Mock
    HybridMergeService mergeService;
    @Mock
    FeatureStatusService featureStatusService;
    @Mock
    FeatureReplaceService featureReplaceService;
    @Mock
    ProcessingProperties properties;
    @Mock
    PipelineJobSupport jobSupport;

    @InjectMocks
    HybridProcessingJob job;

    @Test
    void mergeLoadsPinnedParentSnapshotsOnceNotLatestLiveRows() {
        UUID lakeId = UUID.randomUUID();
        Lake lake = new Lake();
        lake.setId(lakeId);
        lake.setName("Head Lake");
        HybridParentPins pins = new HybridParentPins(
                UUID.randomUUID(),
                "gis-pinned",
                UUID.randomUUID(),
                "vision-pinned",
                "src-shared"
        );
        LakeAnalysisRun analysisRun = new LakeAnalysisRun();
        analysisRun.setId(UUID.randomUUID());
        AnalysisContext context = org.mockito.Mockito.mock(AnalysisContext.class);
        when(context.sourceDatasetSnapshot()).thenReturn(Map.of());
        when(lakeRepository.findById(lakeId)).thenReturn(Optional.of(lake));
        when(properties.toSnapshot()).thenReturn(Map.of());
        when(jobSupport.openRun(eq(lake), eq(Pipeline.HYBRID), any(), any(), any(), eq(pins))).thenReturn(analysisRun);
        when(contextFactory.create(eq(lake), any(), eq(analysisRun.getId()), eq(Pipeline.HYBRID))).thenReturn(context);
        when(featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(lakeId, Pipeline.GIS, "gis-pinned"))
                .thenReturn(List.of());
        when(featureRepository.findByLakeIdAndPipelineAndAnalysisVersion(lakeId, Pipeline.VISION, "vision-pinned"))
                .thenReturn(List.of());
        when(mergeService.merge(any(), any(), any(), eq(context))).thenReturn(List.of());
        when(jobSupport.complete(any(), eq(Pipeline.HYBRID), any(), eq(analysisRun), any(), any(), any(), any()))
                .thenReturn(org.mockito.Mockito.mock(LakeProcessSummaryResponse.class));

        job.run(lakeId, pins);

        verify(featureRepository).findByLakeIdAndPipelineAndAnalysisVersion(lakeId, Pipeline.GIS, "gis-pinned");
        verify(featureRepository).findByLakeIdAndPipelineAndAnalysisVersion(lakeId, Pipeline.VISION, "vision-pinned");
        verify(featureRepository, never()).findByLakeIdAndPipelineAndType(any(), eq(Pipeline.GIS), any());
        verify(featureRepository, never()).findByLakeIdAndPipelineAndType(any(), eq(Pipeline.VISION), any());
        for (FeatureType type : FeatureType.values()) {
            verify(mergeService).merge(eq(type), eq(List.of()), eq(List.of()), eq(context));
        }
    }
}
