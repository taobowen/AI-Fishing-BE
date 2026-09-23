package com.aifishing.lake.ops;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LakeOpsJobRepository extends JpaRepository<LakeOpsJob, UUID> {

    Optional<LakeOpsJob> findFirstByLakeIdAndKindAndDedupeKeyAndStatusIn(
            UUID lakeId,
            LakeOpsJobKind kind,
            String dedupeKey,
            Collection<LakeOpsJobStatus> statuses
    );

    List<LakeOpsJob> findByStatusIn(Collection<LakeOpsJobStatus> statuses);

    @Query("""
            SELECT COUNT(j) FROM LakeOpsJob j
            WHERE j.status IN :statuses
              AND (j.ecsTaskArn IS NOT NULL OR j.launchAttemptedAt IS NOT NULL)
            """)
    long countOccupiedEcsSlots(@Param("statuses") Collection<LakeOpsJobStatus> statuses);

    List<LakeOpsJob> findByStatusAndEcsTaskArnIsNullAndLaunchAttemptedAtIsNullOrderByCreatedAtAsc(
            LakeOpsJobStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LakeOpsJob j
            SET j.status = com.aifishing.lake.ops.LakeOpsJobStatus.RUNNING,
                j.startedAt = :now,
                j.heartbeatAt = :now
            WHERE j.id = :id AND j.status = com.aifishing.lake.ops.LakeOpsJobStatus.QUEUED
            """)
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LakeOpsJob j
            SET j.heartbeatAt = :now
            WHERE j.id = :id AND j.status = com.aifishing.lake.ops.LakeOpsJobStatus.RUNNING
            """)
    int heartbeat(@Param("id") UUID id, @Param("now") Instant now);
}
