package com.aifishing.guidance.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SessionSummaryRepository extends JpaRepository<SessionSummaryEntity, UUID> {

    @Query("""
            select s from SessionSummaryEntity s, com.aifishing.fishingsession.domain.FishingSession fs
            where s.fishingSessionId = fs.id
              and fs.userId = :userId
            order by s.createdAt desc
            """)
    List<SessionSummaryEntity> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);
}
