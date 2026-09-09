package com.aifishing.lake.processing.storage;

import com.aifishing.lake.processing.ProcessingProperties;
import com.aifishing.lake.processing.VisionProperties;
import com.aifishing.lake.processing.domain.DerivedAnalysisArtifact;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.render.RenderedTile;
import com.aifishing.lake.processing.repo.DerivedAnalysisArtifactRepository;
import com.aifishing.lake.processing.surface.DepthSurfaceBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DerivedArtifactService {

    public static final String ARTIFACT_CONTOUR_ANALYSIS = "CONTOUR_ANALYSIS";
    public static final String ARTIFACT_BATHYMETRIC_MAP = "BATHYMETRIC_MAP";
    public static final String ARTIFACT_VISION_RAW = "VISION_RAW_JSON";
    public static final String ARTIFACT_BENCHMARK_REPORT = "BENCHMARK_REPORT";

    private final DerivedAnalysisArtifactRepository repository;
    private final DepthSurfaceBuilder depthSurfaceBuilder;
    private final ProcessingProperties properties;
    private final VisionProperties visionProperties;
    private final ObjectMapper objectMapper;
    private final ArtifactObjectStorage artifactObjectStorage;

    public DerivedArtifactService(
            DerivedAnalysisArtifactRepository repository,
            DepthSurfaceBuilder depthSurfaceBuilder,
            ProcessingProperties properties,
            VisionProperties visionProperties,
            ObjectMapper objectMapper,
            ArtifactObjectStorage artifactObjectStorage
    ) {
        this.repository = repository;
        this.depthSurfaceBuilder = depthSurfaceBuilder;
        this.properties = properties;
        this.visionProperties = visionProperties;
        this.objectMapper = objectMapper;
        this.artifactObjectStorage = artifactObjectStorage;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DerivedAnalysisArtifact persistContourMetadata(AnalysisContext context, Map<String, Object> parameters) {
        Map<String, Object> gridMetadata = depthSurfaceBuilder.contourOnlyMetadata(context);
        String checksum = sha256(Map.of(
                "algorithmVersion", properties.getAlgorithmVersion(),
                "parameters", parameters,
                "sourceDatasetSnapshot", context.sourceDatasetSnapshot(),
                "gridMetadata", gridMetadata
        ));
        String storageUri = null;
        if (properties.isPersistSurfaces()) {
            storageUri = writeOptionalFile(context, gridMetadata, checksum);
        }
        DerivedAnalysisArtifact artifact = new DerivedAnalysisArtifact();
        artifact.setLakeId(context.lake().getId());
        artifact.setAnalysisRunId(context.analysisRunId());
        artifact.setAnalysisVersion(context.analysisVersion());
        artifact.setAlgorithmVersion(properties.getAlgorithmVersion());
        artifact.setArtifactType(ARTIFACT_CONTOUR_ANALYSIS);
        artifact.setParameters(parameters);
        artifact.setSourceDatasetSnapshot(context.sourceDatasetSnapshot());
        artifact.setGridMetadata(gridMetadata);
        artifact.setChecksumSha256(checksum);
        artifact.setStorageUri(storageUri);
        return repository.save(artifact);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DerivedAnalysisArtifact persistMap(AnalysisContext context, List<RenderedTile> tiles, Map<String, Object> parameters) {
        Map<String, Object> metadata = mapMetadata(tiles);
        String checksum = sha256(Map.of(
                "analysisVersion", context.analysisVersion(),
                "tiles", metadata,
                "sourceDatasetSnapshot", context.sourceDatasetSnapshot()
        ));
        String storageUri = null;
        if (visionProperties.isPersistMaps() || properties.isPersistSurfaces()) {
            storageUri = writeMapFiles(context, tiles);
        }
        DerivedAnalysisArtifact artifact = new DerivedAnalysisArtifact();
        artifact.setLakeId(context.lake().getId());
        artifact.setAnalysisRunId(context.analysisRunId());
        artifact.setAnalysisVersion(context.analysisVersion());
        artifact.setAlgorithmVersion(properties.getAlgorithmVersion());
        artifact.setArtifactType(ARTIFACT_BATHYMETRIC_MAP);
        artifact.setParameters(parameters);
        artifact.setSourceDatasetSnapshot(context.sourceDatasetSnapshot());
        artifact.setGridMetadata(metadata);
        artifact.setChecksumSha256(checksum);
        artifact.setStorageUri(storageUri);
        return repository.save(artifact);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DerivedAnalysisArtifact persistJsonArtifact(
            AnalysisContext context,
            String artifactType,
            Map<String, Object> body,
            String fileName
    ) {
        String checksum = sha256(body);
        String storageUri = null;
        if (visionProperties.isPersistMaps() || properties.isPersistSurfaces()) {
            storageUri = writeJson(context, fileName, body);
        }
        DerivedAnalysisArtifact artifact = new DerivedAnalysisArtifact();
        artifact.setLakeId(context.lake().getId());
        artifact.setAnalysisRunId(context.analysisRunId());
        artifact.setAnalysisVersion(context.analysisVersion());
        artifact.setAlgorithmVersion(properties.getAlgorithmVersion());
        artifact.setArtifactType(artifactType);
        artifact.setParameters(Map.of("fileName", fileName));
        artifact.setSourceDatasetSnapshot(context.sourceDatasetSnapshot());
        artifact.setGridMetadata(body);
        artifact.setChecksumSha256(checksum);
        artifact.setStorageUri(storageUri);
        return repository.save(artifact);
    }

    public String directoryPrefix(AnalysisContext context) {
        return context.lake().getId() + "/" + context.analysisVersion();
    }

    private Map<String, Object> mapMetadata(List<RenderedTile> tiles) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tileCount", tiles.size());
        metadata.put("northUp", true);
        metadata.put("srid", 4326);
        List<Map<String, Object>> tileMaps = new ArrayList<>();
        for (RenderedTile tile : tiles) {
            Map<String, Object> row = new LinkedHashMap<>(tile.georef().toMap());
            row.put("row", tile.row());
            row.put("col", tile.col());
            tileMaps.add(row);
        }
        metadata.put("tiles", tileMaps);
        return metadata;
    }

    private String writeMapFiles(AnalysisContext context, List<RenderedTile> tiles) {
        try {
            String prefix = directoryPrefix(context);
            String mapUri = artifactObjectStorage.putRelative(prefix + "/map.png", tiles.get(0).png(), "image/png");
            artifactObjectStorage.putRelative(
                    prefix + "/georef.json",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(tiles.get(0).georef().toMap()),
                    "application/json"
            );
            if (tiles.size() > 1) {
                for (RenderedTile tile : tiles) {
                    artifactObjectStorage.putRelative(
                            prefix + "/tile-r%d-c%d.png".formatted(tile.row(), tile.col()),
                            tile.png(),
                            "image/png"
                    );
                }
            }
            return mapUri;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist bathymetric map", ex);
        }
    }

    private String writeJson(AnalysisContext context, String fileName, Map<String, Object> body) {
        try {
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(body);
            return artifactObjectStorage.putRelative(directoryPrefix(context) + "/" + fileName, json, "application/json");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist " + fileName, ex);
        }
    }

    private String writeOptionalFile(AnalysisContext context, Map<String, Object> gridMetadata, String checksum) {
        try {
            Map<String, Object> body = Map.of(
                    "checksumSha256", checksum,
                    "gridMetadata", gridMetadata,
                    "sourceDatasetSnapshot", context.sourceDatasetSnapshot()
            );
            return writeJson(context, "contour-analysis.json", body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist derived surface metadata", ex);
        }
    }

    private String sha256(Map<String, Object> payload) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | java.security.NoSuchAlgorithmException ex) {
            byte[] fallback = String.valueOf(payload).getBytes(StandardCharsets.UTF_8);
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fallback));
            } catch (java.security.NoSuchAlgorithmException ignored) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
