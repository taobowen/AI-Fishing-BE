package com.aifishing.lake.processing.extract;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.ingestion.domain.BathymetryPoint;
import com.aifishing.lake.ingestion.domain.LakeDatasetStatus;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.geo.LakeWaterGeometry;
import com.aifishing.lake.ingestion.repo.BathymetryContourRepository;
import com.aifishing.lake.ingestion.repo.BathymetryPointRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.dto.Pipeline;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class AnalysisContextFactory {

    static final Set<DatasetType> STRUCTURE_DATASET_TYPES = Set.of(
            DatasetType.LAKE_BOUNDARY,
            DatasetType.SHORELINE,
            DatasetType.ISLAND,
            DatasetType.BATHYMETRY_INDEX,
            DatasetType.BATHYMETRY_LINE,
            DatasetType.BATHYMETRY_POINT
    );

    private final BathymetryContourRepository contourRepository;
    private final BathymetryPointRepository bathymetryPointRepository;
    private final LakeWaterwayRepository waterwayRepository;
    private final LakeWaterGeometry lakeWaterGeometry;
    private final LakeDatasetStatusRepository datasetStatusRepository;
    private final ContourTopology contourTopology;
    private final ProcessingProperties processingProperties;

    public AnalysisContextFactory(
            BathymetryContourRepository contourRepository,
            BathymetryPointRepository bathymetryPointRepository,
            LakeWaterwayRepository waterwayRepository,
            LakeWaterGeometry lakeWaterGeometry,
            LakeDatasetStatusRepository datasetStatusRepository,
            ContourTopology contourTopology,
            ProcessingProperties processingProperties
    ) {
        this.contourRepository = contourRepository;
        this.bathymetryPointRepository = bathymetryPointRepository;
        this.waterwayRepository = waterwayRepository;
        this.lakeWaterGeometry = lakeWaterGeometry;
        this.datasetStatusRepository = datasetStatusRepository;
        this.contourTopology = contourTopology;
        this.processingProperties = processingProperties;
    }

    public AnalysisContext create(Lake lake, String analysisVersion, UUID analysisRunId) {
        return create(lake, analysisVersion, analysisRunId, Pipeline.GIS);
    }

    public AnalysisContext create(Lake lake, String analysisVersion, UUID analysisRunId, Pipeline pipeline) {
        List<BathymetryContour> contours = contourRepository.findByLakeId(lake.getId());
        List<BathymetryPoint> points = bathymetryPointRepository.findByLakeId(lake.getId());
        List<LakeWaterway> shorelines = waterwayRepository.findByLakeIdAndType(lake.getId(), "SHORELINE");
        List<LakeWaterway> waterwayIslands = waterwayRepository.findByLakeIdAndType(lake.getId(), "ISLAND");
        Geometry boundary = lakeWaterGeometry.unionedWater(lake);
        List<LakeWaterway> islands = islandsIncludingBoundaryRings(lake, waterwayIslands, boundary);
        List<LakeDatasetStatus> statuses = datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lake.getId());
        Map<String, Object> snapshot = sourceSnapshot(
                statuses,
                contours.size(),
                contours.stream().min(java.util.Comparator.comparing(BathymetryContour::getId))
                        .map(BathymetryContour::getImportVersion).orElse(null),
                points.size(),
                points.stream().min(java.util.Comparator.comparing(BathymetryPoint::getId))
                        .map(BathymetryPoint::getImportVersion).orElse(null),
                shorelines.size(),
                shorelines.stream().min(java.util.Comparator.comparing(LakeWaterway::getId))
                        .map(LakeWaterway::getImportVersion).orElse(null),
                waterwayIslands.size(),
                waterwayIslands.stream().min(java.util.Comparator.comparing(LakeWaterway::getId))
                        .map(LakeWaterway::getImportVersion).orElse(null),
                boundary
        );
        LakeSourceQuality quality = sourceQuality(statuses, contours, points, shorelines, islands);
        return new AnalysisContext(
                lake,
                analysisVersion,
                analysisRunId,
                pipeline == null ? Pipeline.GIS : pipeline,
                snapshot,
                contours,
                points,
                shorelines,
                islands,
                boundary,
                quality,
                contourTopology.closedPolygons(contours)
        );
    }

    /**
     * Phase 2 fingerprint inputs without loading contour/point/waterway geometries.
     * Must match {@link #create} {@code sourceDatasetSnapshot} for the same lake.
     */
    public Map<String, Object> sourceDatasetSnapshot(Lake lake) {
        List<LakeDatasetStatus> statuses = datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lake.getId());
        long contourCount = contourRepository.countByLakeId(lake.getId());
        long pointCount = bathymetryPointRepository.countByLakeId(lake.getId());
        long shorelineCount = waterwayRepository.countByLakeIdAndType(lake.getId(), "SHORELINE");
        long islandCount = waterwayRepository.countByLakeIdAndType(lake.getId(), "ISLAND");
        String contourImport = contourRepository.findFirstByLakeIdOrderByIdAsc(lake.getId())
                .map(BathymetryContour::getImportVersion)
                .orElse(null);
        String pointImport = bathymetryPointRepository.findFirstByLakeIdOrderByIdAsc(lake.getId())
                .map(BathymetryPoint::getImportVersion)
                .orElse(null);
        String shorelineImport = waterwayRepository.findFirstByLakeIdAndTypeOrderByIdAsc(lake.getId(), "SHORELINE")
                .map(LakeWaterway::getImportVersion)
                .orElse(null);
        String islandImport = waterwayRepository.findFirstByLakeIdAndTypeOrderByIdAsc(lake.getId(), "ISLAND")
                .map(LakeWaterway::getImportVersion)
                .orElse(null);
        Geometry boundary = lakeWaterGeometry.unionedWater(lake);
        return sourceSnapshot(
                statuses,
                contourCount,
                contourImport,
                pointCount,
                pointImport,
                shorelineCount,
                shorelineImport,
                islandCount,
                islandImport,
                boundary
        );
    }

    public boolean bathymetryRunnable(UUID lakeId, AnalysisContext context) {
        return runnable(lakeId, DatasetType.BATHYMETRY_LINE, context.contours())
                || runnable(lakeId, DatasetType.BATHYMETRY_POINT, context.bathymetryPoints());
    }

    public boolean shorelineRunnable(UUID lakeId, AnalysisContext context) {
        return runnable(lakeId, DatasetType.SHORELINE, context.shorelines());
    }

    public boolean islandRunnable(UUID lakeId, AnalysisContext context) {
        return runnable(lakeId, DatasetType.ISLAND, context.islands());
    }

    public DatasetStatusCode datasetStatus(UUID lakeId, DatasetType type) {
        return datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lakeId).stream()
                .filter(status -> status.getDatasetType() == type)
                .map(LakeDatasetStatus::getStatus)
                .findFirst()
                .orElse(null);
    }

    public boolean runnable(UUID lakeId, DatasetType type, List<?> rows) {
        DatasetStatusCode status = datasetStatus(lakeId, type);
        if (status == DatasetStatusCode.NOT_AVAILABLE) {
            return !rows.isEmpty();
        }
        if (status == DatasetStatusCode.AVAILABLE
                || status == DatasetStatusCode.PARTIAL
                || status == DatasetStatusCode.FAILED) {
            return true;
        }
        return !rows.isEmpty();
    }

    private Map<String, Object> sourceSnapshot(
            List<LakeDatasetStatus> statuses,
            long contourCount,
            String contourImportVersion,
            long pointCount,
            String pointImportVersion,
            long shorelineCount,
            String shorelineImportVersion,
            long islandCount,
            String islandImportVersion,
            Geometry boundary
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (LakeDatasetStatus status : statuses) {
            if (!STRUCTURE_DATASET_TYPES.contains(status.getDatasetType())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", status.getStatus().name());
            row.put("recordCount", status.getRecordCount());
            row.put("provider", status.getProvider());
            if (status.getLastSuccessfulImportAt() != null) {
                row.put("lastSuccessfulImportAt", status.getLastSuccessfulImportAt().toString());
            }
            if (status.getSourceUpdatedAt() != null) {
                row.put("sourceUpdatedAt", status.getSourceUpdatedAt().toString());
            }
            snapshot.put(status.getDatasetType().name(), row);
        }
        putGeometryCount(snapshot, "BATHYMETRY_LINE", contourCount, contourImportVersion);
        putGeometryCount(snapshot, "BATHYMETRY_POINT", pointCount, pointImportVersion);
        putGeometryCount(snapshot, "SHORELINE", shorelineCount, shorelineImportVersion);
        putGeometryCount(snapshot, "ISLAND", islandCount, islandImportVersion);
        if (boundary != null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("srid", boundary.getSRID());
            row.put("numPoints", boundary.getNumPoints());
            row.put("area", Math.round(boundary.getArea() * 1_000_000d) / 1_000_000d);
            if (boundary.getEnvelopeInternal() != null) {
                row.put("minX", round6(boundary.getEnvelopeInternal().getMinX()));
                row.put("minY", round6(boundary.getEnvelopeInternal().getMinY()));
                row.put("maxX", round6(boundary.getEnvelopeInternal().getMaxX()));
                row.put("maxY", round6(boundary.getEnvelopeInternal().getMaxY()));
            }
            snapshot.put("LAKE_BOUNDARY_GEOMETRY", row);
        }
        return snapshot;
    }

    /**
     * Legacy analysis runs hashed every imported dataset. Readiness now fingerprints only
     * structure inputs; project stored snapshots to the same subset before comparing.
     */
    public Map<String, Object> structureSourceSubset(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> projected = new LinkedHashMap<>();
        for (DatasetType type : STRUCTURE_DATASET_TYPES) {
            Object row = snapshot.get(type.name());
            if (row != null) {
                projected.put(type.name(), row);
            }
        }
        Object geometry = snapshot.get("LAKE_BOUNDARY_GEOMETRY");
        if (geometry != null) {
            projected.put("LAKE_BOUNDARY_GEOMETRY", geometry);
        }
        return projected;
    }

    @SuppressWarnings("unchecked")
    private void putGeometryCount(Map<String, Object> snapshot, String key, long count, String importVersion) {
        Map<String, Object> row = (Map<String, Object>) snapshot.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        row.put("canonicalCount", count);
        if (importVersion != null) {
            row.put("importVersion", importVersion);
        }
    }

    private List<LakeWaterway> islandsIncludingBoundaryRings(
            Lake lake,
            List<LakeWaterway> waterwayIslands,
            Geometry boundary
    ) {
        List<Geometry> waterwayGeometries = waterwayIslands.stream()
                .map(LakeWaterway::getGeometry)
                .filter(geometry -> geometry != null && !geometry.isEmpty())
                .toList();
        List<Geometry> geometries = IslandGeometries.includingInteriorRings(
                waterwayGeometries,
                boundary,
                processingProperties.getIslandMinAreaM2()
        );
        List<LakeWaterway> islands = new ArrayList<>(waterwayIslands);
        for (Geometry geometry : geometries) {
            if (waterwayGeometries.stream().anyMatch(existing -> sameIsland(existing, geometry))) {
                continue;
            }
            LakeWaterway ring = new LakeWaterway();
            ring.setLakeId(lake.getId());
            ring.setType("ISLAND");
            ring.setGeometry(geometry);
            ring.setSource("BOUNDARY_RING");
            ring.setProvider("LAKE_BOUNDARY");
            ring.setImportVersion("boundary-ring");
            Point centroid = geometry.getCentroid();
            ring.setSourceRecordId(String.format(
                    Locale.US,
                    "boundary-ring:%.5f:%.5f",
                    centroid.getY(),
                    centroid.getX()
            ));
            islands.add(ring);
        }
        return islands;
    }

    private static boolean sameIsland(Geometry existing, Geometry candidate) {
        try {
            return existing.equalsTopo(candidate) || existing.covers(candidate);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private LakeSourceQuality sourceQuality(
            List<LakeDatasetStatus> statuses,
            List<BathymetryContour> contours,
            List<BathymetryPoint> points,
            List<LakeWaterway> shorelines,
            List<LakeWaterway> islands
    ) {
        return new LakeSourceQuality(
                contours.size(),
                points.size(),
                hasStatus(statuses, DatasetType.BATHYMETRY_INDEX, DatasetStatusCode.AVAILABLE, DatasetStatusCode.PARTIAL),
                !contours.isEmpty() || hasStatus(statuses, DatasetType.BATHYMETRY_LINE, DatasetStatusCode.AVAILABLE, DatasetStatusCode.PARTIAL),
                !points.isEmpty() || hasStatus(statuses, DatasetType.BATHYMETRY_POINT, DatasetStatusCode.AVAILABLE, DatasetStatusCode.PARTIAL),
                !shorelines.isEmpty(),
                !islands.isEmpty(),
                contourTopology.meanNearestSpacingM(contours)
        );
    }

    private boolean hasStatus(List<LakeDatasetStatus> statuses, DatasetType type, DatasetStatusCode... expected) {
        return statuses.stream()
                .filter(status -> status.getDatasetType() == type)
                .anyMatch(status -> {
                    for (DatasetStatusCode code : expected) {
                        if (status.getStatus() == code) {
                            return true;
                        }
                    }
                    return false;
                });
    }
}
