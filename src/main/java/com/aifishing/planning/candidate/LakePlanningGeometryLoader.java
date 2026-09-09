package com.aifishing.planning.candidate;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.ingestion.geo.LakeWaterGeometry;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LakePlanningGeometryLoader {

    private final LakeWaterGeometry lakeWaterGeometry;
    private final LakeWaterwayRepository waterwayRepository;

    public LakePlanningGeometryLoader(
            LakeWaterGeometry lakeWaterGeometry,
            LakeWaterwayRepository waterwayRepository
    ) {
        this.lakeWaterGeometry = lakeWaterGeometry;
        this.waterwayRepository = waterwayRepository;
    }

    public LakePlanningGeometry load(Lake lake) {
        Geometry water = lakeWaterGeometry.unionedWater(lake);
        List<Geometry> islands = waterwayRepository.findByLakeIdAndType(lake.getId(), "ISLAND").stream()
                .map(LakeWaterway::getGeometry)
                .filter(geometry -> geometry != null && !geometry.isEmpty())
                .toList();
        if (water != null) {
            Geometry reduced = water;
            for (Geometry island : islands) {
                try {
                    reduced = reduced.difference(island);
                } catch (Exception ignored) {
                    // keep previous water polygon
                }
            }
            water = reduced;
        }
        return new LakePlanningGeometry(water, islands);
    }
}
