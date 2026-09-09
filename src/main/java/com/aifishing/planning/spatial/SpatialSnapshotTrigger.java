package com.aifishing.planning.spatial;

import com.aifishing.lake.processing.dto.LakeProcessingStatus;
import com.aifishing.lake.processing.dto.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SpatialSnapshotTrigger {

    private static final Logger log = LoggerFactory.getLogger(SpatialSnapshotTrigger.class);

    private final SpatialSnapshotJob job;

    public SpatialSnapshotTrigger(SpatialSnapshotJob job) {
        this.job = job;
    }

    public void afterAnalysis(UUID lakeId, Pipeline pipeline, String analysisVersion, LakeProcessingStatus status) {
        if (pipeline == Pipeline.VISION) {
            return;
        }
        if (status != LakeProcessingStatus.READY && status != LakeProcessingStatus.PARTIAL) {
            return;
        }
        try {
            job.submitIfReady(lakeId, pipeline, analysisVersion);
        } catch (RuntimeException ex) {
            log.warn("Spatial snapshot build failed for lake {} pipeline {}: {}", lakeId, pipeline, ex.getMessage());
        }
    }
}
