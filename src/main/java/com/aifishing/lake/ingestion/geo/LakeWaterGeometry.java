package com.aifishing.lake.ingestion.geo;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.PolygonalGeometries;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.LakeBoundaryRecord;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LakeWaterGeometry {

    private final LakeBoundaryRecordRepository boundaryRepository;

    public LakeWaterGeometry(LakeBoundaryRecordRepository boundaryRepository) {
        this.boundaryRepository = boundaryRepository;
    }

    public Geometry unionedWater(Lake lake) {
        List<Geometry> parts = new ArrayList<>();
        for (LakeBoundaryRecord record : boundaryRepository.findByLakeId(lake.getId())) {
            Geometry geometry = record.getGeometry();
            if (geometry != null && !geometry.isEmpty()) {
                Geometry copy = geometry.copy();
                copy.setSRID(GeoMapper.SRID);
                parts.add(copy);
            }
        }
        if (parts.isEmpty()) {
            if (lake.getBoundary() == null || lake.getBoundary().isEmpty()) {
                return null;
            }
            Geometry fallback = lake.getBoundary().copy();
            fallback.setSRID(GeoMapper.SRID);
            return polygonalWater(fallback);
        }
        Geometry union;
        try {
            union = parts.size() == 1 ? parts.get(0) : UnaryUnionOp.union(parts);
        } catch (Exception ex) {
            union = sequentialUnion(parts);
        }
        if (union == null || union.isEmpty()) {
            return null;
        }
        union.setSRID(GeoMapper.SRID);
        return polygonalWater(union);
    }

    private static Geometry sequentialUnion(List<Geometry> parts) {
        Geometry union = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            try {
                union = union.union(parts.get(i));
            } catch (Exception ignored) {
                // keep previous union if a part is topologically invalid
            }
        }
        return union;
    }

    private static Geometry polygonalWater(Geometry geometry) {
        Geometry polygonal = PolygonalGeometries.of(geometry);
        if (polygonal == null || polygonal.isEmpty()) {
            return null;
        }
        polygonal.setSRID(GeoMapper.SRID);
        return polygonal;
    }
}
