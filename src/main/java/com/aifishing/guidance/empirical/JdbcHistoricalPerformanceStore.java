package com.aifishing.guidance.empirical;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcHistoricalPerformanceStore implements HistoricalPerformanceStore {

    @PersistenceContext
    private EntityManager entityManager;

    private final GuidanceProperties properties;
    private final Clock clock;

    public JdbcHistoricalPerformanceStore(GuidanceProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void rebuildGrains(Collection<EmpiricalGrain> grains) {
        if (grains == null || grains.isEmpty()) {
            return;
        }
        List<EmpiricalGrain> ordered = new ArrayList<>(grains);
        ordered.sort(EmpiricalGrain::compareTo);
        Instant now = clock.instant();
        for (EmpiricalGrain grain : ordered) {
            lockGrain(grain, now);
            EmpiricalRawCounts raw = sumContributions(grain);
            if (raw.isEmpty()) {
                Query delete = entityManager.createNativeQuery("""
                                DELETE FROM historical_performance
                                WHERE %s
                                """.formatted(EmpiricalSql.GRAIN_PREDICATE));
                EmpiricalSql.bindGrain(delete, grain);
                delete.executeUpdate();
                continue;
            }
            EmpiricalAlgorithm.Derived derived = EmpiricalAlgorithm.derive(raw, properties.getEmpirical());
            Query update = entityManager.createNativeQuery("""
                    UPDATE historical_performance
                    SET fishing_effort_seconds = :effort,
                        bite_count = :bites,
                        fish_on_count = :fishOn,
                        landed_count = :landed,
                        contributing_session_waypoint_count = :waypoints,
                        effort_minutes = :effortMinutes,
                        bite_rate = :biteRate,
                        fish_on_rate = :fishOnRate,
                        landing_rate = :landingRate,
                        cpue = :cpue,
                        smoothed_score = :smoothedScore,
                        sample_confidence = :sampleConfidence,
                        updated_at = :updatedAt
                    WHERE %s
                    """.formatted(EmpiricalSql.GRAIN_PREDICATE));
            EmpiricalSql.bindGrain(update, grain);
            update.setParameter("effort", raw.fishingEffortSeconds());
            update.setParameter("bites", raw.biteCount());
            update.setParameter("fishOn", raw.fishOnCount());
            update.setParameter("landed", raw.landedCount());
            update.setParameter("waypoints", raw.contributingSessionWaypointCount());
            update.setParameter("effortMinutes", derived.effortMinutes());
            update.setParameter("biteRate", derived.biteRate());
            update.setParameter("fishOnRate", derived.fishOnRate());
            update.setParameter("landingRate", derived.landingRate());
            update.setParameter("cpue", derived.cpue());
            update.setParameter("smoothedScore", derived.smoothedScore());
            update.setParameter("sampleConfidence", derived.sampleConfidence());
            update.setParameter("updatedAt", now);
            update.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private void lockGrain(EmpiricalGrain grain, Instant now) {
        Query select = entityManager.createNativeQuery("""
                SELECT id FROM historical_performance
                WHERE %s
                FOR UPDATE
                """.formatted(EmpiricalSql.GRAIN_PREDICATE));
        EmpiricalSql.bindGrain(select, grain);
        List<Object> rows = select.getResultList();
        if (!rows.isEmpty()) {
            return;
        }
        try {
            Query insert = entityManager.createNativeQuery("""
                    INSERT INTO historical_performance (
                        id, schema_version, lake_id, zone_id, trip_waypoint_id, species, season_bucket,
                        time_bucket, structure, wind_bucket, wind_direction_bucket, lure_family,
                        empirical_algorithm_version, fishing_effort_seconds, bite_count, fish_on_count,
                        landed_count, contributing_session_waypoint_count, updated_at
                    ) VALUES (
                        :id, :schemaVersion, :lakeId, :zoneId, :tripWaypointId, :species, :seasonBucket,
                        :timeBucket, :structure, :windBucket, :windDirectionBucket, :lureFamily,
                        :algorithmVersion, 0, 0, 0, 0, 0, :updatedAt
                    )
                    ON CONFLICT ON CONSTRAINT historical_performance_grain_key DO NOTHING
                    """);
            EmpiricalSql.bindGrain(insert, grain);
            insert.setParameter("id", UUID.randomUUID());
            insert.setParameter("schemaVersion", GuidanceSchemaVersion.VALUE);
            insert.setParameter("updatedAt", now);
            insert.executeUpdate();
        } catch (RuntimeException ignored) {
            // Concurrent insert of the same grain is expected; lock the winner below.
        }
        Query locked = entityManager.createNativeQuery("""
                SELECT id FROM historical_performance
                WHERE %s
                FOR UPDATE
                """.formatted(EmpiricalSql.GRAIN_PREDICATE));
        EmpiricalSql.bindGrain(locked, grain);
        if (locked.getResultList().isEmpty()) {
            throw new IllegalStateException("Failed to lock historical_performance grain");
        }
    }

    private EmpiricalRawCounts sumContributions(EmpiricalGrain grain) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(fishing_effort_seconds), 0),
                       COALESCE(SUM(bite_count), 0),
                       COALESCE(SUM(fish_on_count), 0),
                       COALESCE(SUM(landed_count), 0),
                       COALESCE(SUM(contributing_session_waypoint_count), 0)
                FROM historical_performance_contributions
                WHERE %s
                """.formatted(EmpiricalSql.GRAIN_PREDICATE));
        EmpiricalSql.bindGrain(query, grain);
        Object[] row = (Object[]) query.getSingleResult();
        if (row == null) {
            return EmpiricalRawCounts.ZERO;
        }
        return new EmpiricalRawCounts(
                EmpiricalSql.asLong(row[0]),
                EmpiricalSql.asInt(row[1]),
                EmpiricalSql.asInt(row[2]),
                EmpiricalSql.asInt(row[3]),
                EmpiricalSql.asInt(row[4])
        );
    }
}
