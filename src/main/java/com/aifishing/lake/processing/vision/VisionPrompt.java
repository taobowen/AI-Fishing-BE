package com.aifishing.lake.processing.vision;

import com.aifishing.lake.processing.VisionProperties;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.render.GeorefTransform;

public final class VisionPrompt {

    private VisionPrompt() {
    }

    public static String forTile(AnalysisContext context, GeorefTransform georef, VisionProperties properties) {
        return """
                You are labeling major fishing-relevant lake structure on a north-up bathymetric map.
                Do not give fishing advice, routes, or weather. Identify only these types:
                HUMP, DROP_OFF, FLAT, POINT, BASIN, ISLAND_EDGE.

                Pixel (0,0) is the northwest corner. Pixel x increases east, y increases south.
                Image size: %dx%d pixels.
                Geographic bbox EPSG:4326: minLng=%s minLat=%s maxLng=%s maxLat=%s.
                Prompt version: %s.
                Lake name: %s.

                Return ONLY JSON:
                {"features":[{"type":"HUMP","geometryType":"Polygon","coordinates":[[[x,y],...]],"confidence":0.0,"evidence":"..."}]}
                coordinates are PIXEL [x,y]. Polygon rings must close. DROP_OFF uses LineString. POINT uses Point [x,y].
                Prefer major, clearly visible structures. Skip tiny noise.
                """.formatted(
                georef.widthPx(),
                georef.heightPx(),
                georef.minLng(),
                georef.minLat(),
                georef.maxLng(),
                georef.maxLat(),
                properties.getPromptVersion(),
                context.lake().getName()
        );
    }
}
