package com.aifishing.lake.processing.vision;

import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.render.RenderedTile;

import java.util.List;

public interface VisionMapClient {

    List<VisionCandidate> extract(RenderedTile tile, AnalysisContext context);
}
