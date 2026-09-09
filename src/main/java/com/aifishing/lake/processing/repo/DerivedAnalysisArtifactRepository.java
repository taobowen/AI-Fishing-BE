package com.aifishing.lake.processing.repo;

import com.aifishing.lake.processing.domain.DerivedAnalysisArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DerivedAnalysisArtifactRepository extends JpaRepository<DerivedAnalysisArtifact, UUID> {

    List<DerivedAnalysisArtifact> findByLakeIdAndAnalysisVersion(UUID lakeId, String analysisVersion);

    Optional<DerivedAnalysisArtifact> findFirstByLakeIdAndArtifactTypeOrderByCreatedAtDesc(
            UUID lakeId,
            String artifactType
    );
}
