package com.aifishing.lake.ingestion.admin;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.LakeFishSpecies;
import com.aifishing.lake.ingestion.job.LakeDataIngestionService;
import com.aifishing.lake.ingestion.repo.LakeFishSpeciesRepository;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.repo.LakeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LakeBootstrapValidationService {

    static final double ABSURD_POLYGON_AREA_M2 = 1_000_000_000d;
    static final double ABSURD_LINE_LENGTH_M = 500_000d;

    private final LakeRepository lakeRepository;
    private final LakeDataIngestionService ingestionService;
    private final LakeFishSpeciesRepository fishSpeciesRepository;
    private final JdbcTemplate jdbcTemplate;

    public LakeBootstrapValidationService(
            LakeRepository lakeRepository,
            LakeDataIngestionService ingestionService,
            LakeFishSpeciesRepository fishSpeciesRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.lakeRepository = lakeRepository;
        this.ingestionService = ingestionService;
        this.fishSpeciesRepository = fishSpeciesRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public LakeBootstrapValidationResponse validate(UUID lakeId) {
        Lake lake = lakeRepository.findById(lakeId)
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        List<String> warnings = new ArrayList<>();
        String postgisVersion = postgisVersion();
        boolean postgisAvailable = postgisVersion != null;
        if (!postgisAvailable) {
            warnings.add("PostGIS is not available");
        }
        Double lakeArea = lakeAreaM2(lakeId);
        LakeBootstrapValidationResponse.GeometrySanityReport geometry = geometrySanity(lakeId, lake.getBoundary() != null);
        warnings.addAll(geometry.warnings());
        List<String> mappedSpecies = fishSpeciesRepository.findByLakeId(lakeId).stream()
                .map(LakeFishSpecies::getSpecies)
                .filter(Objects::nonNull)
                .map(FishSpecies::name)
                .distinct()
                .sorted()
                .toList();
        long catches = count("SELECT COUNT(*) FROM catch_events ce JOIN trips t ON t.id = ce.trip_id WHERE t.lake_id = ?", lakeId);
        long effort = count("""
                SELECT COUNT(*) FROM fishing_effort_segments e
                JOIN fishing_sessions fs ON fs.id = e.fishing_session_id
                JOIN trips t ON t.id = fs.trip_id
                WHERE t.lake_id = ?
                """, lakeId);
        boolean coldStart = catches == 0 && effort == 0;
        if (!coldStart) {
            warnings.add("Empirical tables are not empty; historical ranking may not be cold-start 0.5/0");
        }
        return new LakeBootstrapValidationResponse(
                lake.getId(),
                lake.getName(),
                lake.getOgfId(),
                lake.getOfficialName(),
                lake.getTimeZoneId(),
                lake.getBoundary() != null,
                lakeArea,
                postgisVersion,
                postgisAvailable,
                ingestionService.summary(lakeId),
                geometry,
                mappedSpecies,
                coldStart,
                catches,
                effort,
                warnings
        );
    }

    private LakeBootstrapValidationResponse.GeometrySanityReport geometrySanity(UUID lakeId, boolean hasBoundary) {
        List<String> warnings = new ArrayList<>();
        Long featureCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lake_features WHERE lake_id = ? AND pipeline = ?",
                Long.class,
                lakeId,
                Pipeline.GIS.name()
        );
        int features = featureCount == null ? 0 : featureCount.intValue();
        int invalid = count("SELECT COUNT(*) FROM lake_features WHERE lake_id = ? AND pipeline = ? AND NOT ST_IsValid(geometry)",
                lakeId, Pipeline.GIS.name());
        int wrongSrid = count("SELECT COUNT(*) FROM lake_features WHERE lake_id = ? AND pipeline = ? AND ST_SRID(geometry) <> 4326",
                lakeId, Pipeline.GIS.name());
        int outside = 0;
        if (hasBoundary) {
            outside = count("""
                    SELECT COUNT(*) FROM lake_features f
                    JOIN lakes l ON l.id = f.lake_id
                    WHERE f.lake_id = ? AND f.pipeline = ?
                      AND l.boundary IS NOT NULL
                      AND NOT ST_Intersects(f.geometry, l.boundary)
                    """, lakeId, Pipeline.GIS.name());
        }
        int confidenceBad = count("""
                SELECT COUNT(*) FROM lake_features
                WHERE lake_id = ? AND pipeline = ? AND (confidence < 0 OR confidence > 1)
                """, lakeId, Pipeline.GIS.name());
        int polygonAbsurd = count("""
                SELECT COUNT(*) FROM lake_features
                WHERE lake_id = ? AND pipeline = ?
                  AND ST_GeometryType(geometry) IN ('ST_Polygon', 'ST_MultiPolygon')
                  AND (
                    ST_Area(geometry::geography) <= 0
                    OR ST_Area(geometry::geography) > ?
                  )
                """, lakeId, Pipeline.GIS.name(), ABSURD_POLYGON_AREA_M2);
        int lineAbsurd = count("""
                SELECT COUNT(*) FROM lake_features
                WHERE lake_id = ? AND pipeline = ?
                  AND ST_GeometryType(geometry) IN ('ST_LineString', 'ST_MultiLineString')
                  AND (
                    ST_Length(geometry::geography) <= 0
                    OR ST_Length(geometry::geography) > ?
                  )
                """, lakeId, Pipeline.GIS.name(), ABSURD_LINE_LENGTH_M);
        int points = count("""
                SELECT COUNT(*) FROM lake_features
                WHERE lake_id = ? AND pipeline = ?
                  AND ST_GeometryType(geometry) IN ('ST_Point', 'ST_MultiPoint')
                """, lakeId, Pipeline.GIS.name());
        int duplicateGroups = 0;
        int duplicateFeatures = 0;
        int[] dup = jdbcTemplate.query("""
                        SELECT COALESCE(COUNT(*), 0) AS grp_count, COALESCE(SUM(cnt), 0) AS feature_count FROM (
                            SELECT COUNT(*) AS cnt
                            FROM lake_features
                            WHERE lake_id = ? AND pipeline = ?
                            GROUP BY md5(ST_AsEWKB(ST_Normalize(geometry)))
                            HAVING COUNT(*) > 1
                        ) d
                        """,
                rs -> {
                    if (!rs.next()) {
                        return new int[]{0, 0};
                    }
                    return new int[]{rs.getInt("grp_count"), rs.getInt("feature_count")};
                },
                lakeId,
                Pipeline.GIS.name());
        if (dup != null) {
            duplicateGroups = dup[0];
            duplicateFeatures = dup[1];
        }
        if (invalid > 0) {
            warnings.add(invalid + " features failed ST_IsValid");
        }
        if (wrongSrid > 0) {
            warnings.add(wrongSrid + " features are not SRID 4326");
        }
        if (outside > 0) {
            warnings.add(outside + " features do not intersect the lake boundary");
        }
        if (confidenceBad > 0) {
            warnings.add(confidenceBad + " features have confidence outside [0,1]");
        }
        if (polygonAbsurd > 0) {
            warnings.add(polygonAbsurd + " polygon features have zero or absurd area");
        }
        if (lineAbsurd > 0) {
            warnings.add(lineAbsurd + " line features have zero or absurd length");
        }
        if (duplicateGroups > 0) {
            warnings.add(duplicateGroups + " exact-duplicate geometry groups (" + duplicateFeatures + " features)");
        }
        return new LakeBootstrapValidationResponse.GeometrySanityReport(
                features,
                invalid,
                wrongSrid,
                outside,
                confidenceBad,
                polygonAbsurd,
                lineAbsurd,
                points,
                duplicateGroups,
                duplicateFeatures,
                warnings
        );
    }

    private String postgisVersion() {
        try {
            return jdbcTemplate.queryForObject("SELECT PostGIS_Version()", String.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private Double lakeAreaM2(UUID lakeId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT ST_Area(boundary::geography) FROM lakes WHERE id = ? AND boundary IS NOT NULL",
                    Double.class,
                    lakeId
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private int count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value.intValue();
    }
}
