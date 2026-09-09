package com.aifishing.feedback.effort.repo;

import com.aifishing.feedback.effort.domain.EffortSegmentType;
import com.aifishing.feedback.effort.domain.FishingEffortSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FishingEffortSegmentRepository extends JpaRepository<FishingEffortSegment, UUID> {

    List<FishingEffortSegment> findByFishingSessionIdOrderByStartedAtAsc(UUID fishingSessionId);

    List<FishingEffortSegment> findByFishingSessionIdAndSegmentTypeOrderByStartedAtAsc(
            UUID fishingSessionId,
            EffortSegmentType segmentType
    );

    void deleteByFishingSessionId(UUID fishingSessionId);

    long countByFishingSessionId(UUID fishingSessionId);
}
