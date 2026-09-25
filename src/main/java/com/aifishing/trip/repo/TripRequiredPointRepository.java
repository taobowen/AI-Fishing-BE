package com.aifishing.trip.repo;

import com.aifishing.trip.domain.TripRequiredPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripRequiredPointRepository extends JpaRepository<TripRequiredPoint, UUID> {

    List<TripRequiredPoint> findByTripIdOrderBySortOrderAscIdAsc(UUID tripId);

    void deleteByTripId(UUID tripId);
}
