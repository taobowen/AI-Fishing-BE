package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.OnlineMetricGrain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GuidanceOnlineMetricRollupRepository extends JpaRepository<GuidanceOnlineMetricRollupEntity, UUID> {

    Optional<GuidanceOnlineMetricRollupEntity> findByWindowStartAndWindowEndAndGrainAndAttributionDimension(
            Instant windowStart,
            Instant windowEnd,
            OnlineMetricGrain grain,
            AttributionDimension attributionDimension
    );
}
