package com.aifishing.lake.processing.repo;

import com.aifishing.lake.processing.domain.LakeAnalysisRun;
import com.aifishing.lake.processing.dto.Pipeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LakeAnalysisRunRepository extends JpaRepository<LakeAnalysisRun, UUID> {

    Optional<LakeAnalysisRun> findFirstByLakeIdOrderByStartedAtDesc(UUID lakeId);

    Optional<LakeAnalysisRun> findFirstByLakeIdAndPipelineOrderByStartedAtDesc(UUID lakeId, Pipeline pipeline);

    List<LakeAnalysisRun> findByLakeIdOrderByStartedAtDesc(UUID lakeId);
}
