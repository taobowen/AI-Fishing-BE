package com.aifishing.lake.processing.surface;

import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.extract.AnalysisContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Surfaces are optional and local/tiled. Phase 3 extractors use contour/topology first,
 * so this builder records that no whole-lake dense raster was created.
 */
@Component
public class DepthSurfaceBuilder {

    private final ProcessingProperties properties;

    public DepthSurfaceBuilder(ProcessingProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> contourOnlyMetadata(AnalysisContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("strategy", "CONTOUR_TOPOLOGY");
        metadata.put("wholeLakeRaster", false);
        metadata.put("postgisRasterUsed", false);
        metadata.put("javaIdwUsed", false);
        metadata.put("tileSizeM", properties.getTileSizeM());
        metadata.put("closedContourCount", context.closedContours().size());
        metadata.put("contourCount", context.contours().size());
        metadata.put("bathymetryPointCount", context.bathymetryPoints().size());
        metadata.put("persistSurfaces", properties.isPersistSurfaces());
        return metadata;
    }
}
