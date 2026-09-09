package com.aifishing.lake.processing;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.lake.ingestion.repo.BathymetryContourRepository;
import com.aifishing.lake.ingestion.repo.BathymetryPointRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureStatusCode;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.FlatExtractor;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.repo.LakeFeatureStatusRepository;
import com.aifishing.seed.DevSeedIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LakeProcessingPartialIT extends AbstractIntegrationTest {

    @MockitoSpyBean
    FlatExtractor flatExtractor;

    @Autowired
    BathymetryContourRepository contourRepository;

    @Autowired
    BathymetryPointRepository bathymetryPointRepository;

    @Autowired
    LakeWaterwayRepository waterwayRepository;

    @Autowired
    LakeBoundaryRecordRepository boundaryRepository;

    @Autowired
    LakeDatasetStatusRepository datasetStatusRepository;

    @Autowired
    LakeFeatureRepository featureRepository;

    @Autowired
    LakeFeatureStatusRepository featureStatusRepository;

    @Test
    void humpSuccessFlatFailureKeepsLastGoodFlatAndMarksPartial() throws Exception {
        ProcessingFixtures.seedFullStructure(
                DevSeedIds.LAKE_ID, 44.75, -78.92,
                contourRepository, bathymetryPointRepository, waterwayRepository,
                boundaryRepository, datasetStatusRepository
        );

        mockMvc.perform(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("READY"));

        List<LakeFeature> firstFlats = featureRepository.findByLakeIdAndType(DevSeedIds.LAKE_ID, FeatureType.FLAT);
        assertThat(firstFlats).isNotEmpty();
        List<UUID> flatIds = firstFlats.stream().map(LakeFeature::getId).toList();
        Instant flatSuccessAt = featureStatusRepository
                .findByLakeIdAndFeatureType(DevSeedIds.LAKE_ID, FeatureType.FLAT)
                .orElseThrow()
                .getLastSuccessfulAnalysisAt();
        String firstHumpVersion = featureRepository.findByLakeIdAndType(DevSeedIds.LAKE_ID, FeatureType.HUMP)
                .get(0)
                .getAnalysisVersion();

        doThrow(new IllegalStateException("synthetic flat failure"))
                .when(flatExtractor)
                .extract(any(AnalysisContext.class));

        mockMvc.perform(asDev(post("/api/v1/admin/lakes/" + DevSeedIds.LAKE_ID + "/process")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processingStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.failedFeatureTypes[0]").value("FLAT"));

        List<LakeFeature> retainedFlats = featureRepository.findByLakeIdAndType(DevSeedIds.LAKE_ID, FeatureType.FLAT);
        assertThat(retainedFlats).extracting(LakeFeature::getId).containsExactlyElementsOf(flatIds);
        assertThat(featureStatusRepository.findByLakeIdAndFeatureType(DevSeedIds.LAKE_ID, FeatureType.FLAT)
                .orElseThrow().getStatus()).isEqualTo(FeatureStatusCode.FAILED);
        assertThat(featureStatusRepository.findByLakeIdAndFeatureType(DevSeedIds.LAKE_ID, FeatureType.FLAT)
                .orElseThrow().getLastSuccessfulAnalysisAt()).isEqualTo(flatSuccessAt);

        List<LakeFeature> humps = featureRepository.findByLakeIdAndType(DevSeedIds.LAKE_ID, FeatureType.HUMP);
        assertThat(humps).isNotEmpty();
        assertThat(humps.get(0).getAnalysisVersion()).isNotEqualTo(firstHumpVersion);
        assertThat(featureStatusRepository.findByLakeIdAndFeatureType(DevSeedIds.LAKE_ID, FeatureType.HUMP)
                .orElseThrow().getStatus()).isEqualTo(FeatureStatusCode.AVAILABLE);
    }
}
