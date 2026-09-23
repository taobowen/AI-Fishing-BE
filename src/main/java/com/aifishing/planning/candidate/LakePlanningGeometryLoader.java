package com.aifishing.planning.candidate;

import com.aifishing.common.geo.PolygonalGeometries;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.ingestion.geo.LakeWaterGeometry;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.extract.IslandGeometries;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LakePlanningGeometryLoader {

    private final LakeWaterGeometry lakeWaterGeometry;
    private final LakeWaterwayRepository waterwayRepository;
    private final ProcessingProperties processingProperties;

    public LakePlanningGeometryLoader(
            LakeWaterGeometry lakeWaterGeometry,
            LakeWaterwayRepository waterwayRepository,
            ProcessingProperties processingProperties
    ) {
        this.lakeWaterGeometry = lakeWaterGeometry;
        this.waterwayRepository = waterwayRepository;
        this.processingProperties = processingProperties;
    }

    public LakePlanningGeometry load(Lake lake) {
        Geometry water = lakeWaterGeometry.unionedWater(lake);
        List<Geometry> waterwayIslands = waterwayRepository.findByLakeIdAndType(lake.getId(), "ISLAND").stream()
                .map(LakeWaterway::getGeometry)
                .filter(geometry -> geometry != null && !geometry.isEmpty())
                .toList();
        List<Geometry> islands = IslandGeometries.includingInteriorRings(
                waterwayIslands,
                water,
                processingProperties.getIslandMinAreaM2()
        );
        if (water != null) {
            Geometry reduced = water;
            for (Geometry island : islands) {
                try {
                    reduced = reduced.difference(island);
                } catch (Exception ignored) {
                    // keep previous water polygon
                }
            }
            water = PolygonalGeometries.of(reduced);
        }
        return new LakePlanningGeometry(water, islands);
    }
}
