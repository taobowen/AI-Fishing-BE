package com.aifishing.lake.processing.render;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.domain.DerivedAnalysisArtifact;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.AnalysisContext;
import com.aifishing.lake.processing.extract.AnalysisContextFactory;
import com.aifishing.lake.processing.repo.DerivedAnalysisArtifactRepository;
import com.aifishing.lake.processing.storage.ArtifactObjectStorage;
import com.aifishing.lake.processing.storage.DerivedArtifactService;
import com.aifishing.lake.repo.LakeRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CanonicalMapService {

    private final LakeRepository lakeRepository;
    private final DerivedAnalysisArtifactRepository artifactRepository;
    private final AnalysisContextFactory contextFactory;
    private final BathymetricMapRenderer renderer;
    private final ArtifactObjectStorage artifactObjectStorage;

    public CanonicalMapService(
            LakeRepository lakeRepository,
            DerivedAnalysisArtifactRepository artifactRepository,
            AnalysisContextFactory contextFactory,
            BathymetricMapRenderer renderer,
            ArtifactObjectStorage artifactObjectStorage
    ) {
        this.lakeRepository = lakeRepository;
        this.artifactRepository = artifactRepository;
        this.contextFactory = contextFactory;
        this.renderer = renderer;
        this.artifactObjectStorage = artifactObjectStorage;
    }

    public byte[] png(UUID lakeId) {
        DerivedAnalysisArtifact artifact = artifactRepository
                .findFirstByLakeIdAndArtifactTypeOrderByCreatedAtDesc(lakeId, DerivedArtifactService.ARTIFACT_BATHYMETRIC_MAP)
                .orElse(null);
        if (artifact != null && artifact.getStorageUri() != null) {
            try {
                byte[] stored = artifactObjectStorage.read(artifact.getStorageUri()).orElse(null);
                if (stored != null && stored.length > 0) {
                    return stored;
                }
            } catch (Exception ignored) {
                // fall through to a live render
            }
        }
        Lake lake = lakeRepository.findById(lakeId).orElseThrow(() -> new NotFoundException("Lake not found"));
        AnalysisContext context = contextFactory.create(lake, "map-preview", UUID.randomUUID(), Pipeline.GIS);
        List<RenderedTile> tiles = renderer.render(context);
        if (tiles.isEmpty()) {
            throw new NotFoundException("Canonical map is not available");
        }
        return tiles.get(0).png();
    }
}
