package com.aifishing.trip.repo;

import com.aifishing.common.enums.TripStatus;
import com.aifishing.trip.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT t FROM Trip t
            WHERE t.userId = :userId
              AND (:status IS NULL OR t.status = :status)
              AND (:status IS NOT NULL OR t.status <> com.aifishing.common.enums.TripStatus.CANCELLED)
              AND (:fromDate IS NULL OR t.plannedDate >= :fromDate)
              AND (:toDate IS NULL OR t.plannedDate <= :toDate)
            ORDER BY t.plannedDate DESC, t.fishingStartTime DESC
            """)
    List<Trip> findOwned(
            @Param("userId") UUID userId,
            @Param("status") TripStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
