package com.aifishing.lake.ops;

import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.processing.dto.Pipeline;

public final class LakeOpsDedupe {

    private LakeOpsDedupe() {
    }

    public static String importKey(DatasetType dataset) {
        return dataset == null ? "*" : dataset.name();
    }

    public static String processKey(Pipeline pipeline) {
        return (pipeline == null ? Pipeline.GIS : pipeline).name();
    }

    public static String snapshotKey(Pipeline pipeline, String analysisVersion) {
        String pipe = (pipeline == null ? Pipeline.GIS : pipeline).name();
        String version = analysisVersion == null || analysisVersion.isBlank() ? "*" : analysisVersion.trim();
        return pipe + "|" + version;
    }
}
