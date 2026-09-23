package com.aifishing.guidance.empirical;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

@Repository
public class JdbcHistoricalContributionStore implements HistoricalContributionStore {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public Set<EmpiricalGrain> replaceSession(
            UUID fishingSessionId,
            Collection<SessionContribution> contributions,
            Instant rebuiltAt,
            String sourceHash
    ) {
        List<Object[]> existing = entityManager.createNativeQuery("""
                        SELECT lake_id, zone_id, trip_waypoint_id, species, season_bucket, time_bucket,
                               structure, wind_bucket, wind_direction_bucket, lure_family, empirical_algorithm_version
                        FROM historical_performance_contributions
                        WHERE fishing_session_id = :sessionId
                        FOR UPDATE
                        """)
                .setParameter("sessionId", fishingSessionId)
                .getResultList();
        Set<EmpiricalGrain> affected = new LinkedHashSet<>();
        for (Object[] row : existing) {
            affected.add(EmpiricalSql.grain(
                    row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8], row[9], row[10]));
        }
        entityManager.createNativeQuery("""
                        DELETE FROM historical_performance_contributions
                        WHERE fishing_session_id = :sessionId
                        """)
                .setParameter("sessionId", fishingSessionId)
                .executeUpdate();
        entityManager.flush();
        Instant at = rebuiltAt == null ? Instant.now() : rebuiltAt;
        for (SessionContribution contribution : contributions == null ? List.<SessionContribution>of() : contributions) {
            if (contribution == null || contribution.grain() == null || contribution.raw() == null) {
                continue;
            }
            EmpiricalGrain grain = contribution.grain();
            EmpiricalRawCounts raw = contribution.raw();
            entityManager.createNativeQuery("""
                            INSERT INTO historical_performance_contributions (
                                id, fishing_session_id, lake_id, zone_id, trip_waypoint_id, species, season_bucket,
                                time_bucket, structure, wind_bucket, wind_direction_bucket, lure_family,
                                empirical_algorithm_version, fishing_effort_seconds, bite_count, fish_on_count,
                                landed_count, contributing_session_waypoint_count, source_hash, rebuilt_at,
                                created_at, updated_at
                            ) VALUES (
                                :id, :sessionId, :lakeId, :zoneId, :tripWaypointId, :species, :seasonBucket,
                                :timeBucket, :structure, :windBucket, :windDirectionBucket, :lureFamily,
                                :algorithmVersion, :effort, :bites, :fishOn, :landed, :waypoints, :sourceHash,
                                :rebuiltAt, :rebuiltAt, :rebuiltAt
                            )
                            """)
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("sessionId", fishingSessionId)
                    .setParameter("lakeId", grain.lakeId())
                    .setParameter("zoneId", grain.zoneId())
                    .setParameter("tripWaypointId", grain.tripWaypointId())
                    .setParameter("species", EmpiricalSql.name(grain.species()))
                    .setParameter("seasonBucket", EmpiricalSql.name(grain.seasonBucket()))
                    .setParameter("timeBucket", EmpiricalSql.name(grain.timeBucket()))
                    .setParameter("structure", EmpiricalSql.name(grain.structure()))
                    .setParameter("windBucket", EmpiricalSql.name(grain.windBucket()))
                    .setParameter("windDirectionBucket", EmpiricalSql.name(grain.windDirectionBucket()))
                    .setParameter("lureFamily", EmpiricalSql.name(grain.lureFamily()))
                    .setParameter("algorithmVersion", grain.algorithmVersion())
                    .setParameter("effort", raw.fishingEffortSeconds())
                    .setParameter("bites", raw.biteCount())
                    .setParameter("fishOn", raw.fishOnCount())
                    .setParameter("landed", raw.landedCount())
                    .setParameter("waypoints", raw.contributingSessionWaypointCount())
                    .setParameter("sourceHash", sourceHash)
                    .setParameter("rebuiltAt", at)
                    .executeUpdate();
            affected.add(grain);
        }
        return affected;
    }

    @Override
    public EmpiricalRawCounts sumMatching(EmpiricalMatch match) {
        if (match == null) {
            return EmpiricalRawCounts.ZERO;
        }
        StringJoiner where = new StringJoiner(" AND ");
        where.add("empirical_algorithm_version = :algorithmVersion");
        if (match.constrains(EmpiricalMatch.Dimension.LAKE)) {
            where.add("lake_id IS NOT DISTINCT FROM :lakeId");
        }
        if (match.constrains(EmpiricalMatch.Dimension.ZONE)) {
            where.add("zone_id IS NOT DISTINCT FROM :zoneId");
        }
        if (match.constrains(EmpiricalMatch.Dimension.WAYPOINT)) {
            where.add("trip_waypoint_id IS NOT DISTINCT FROM :tripWaypointId");
        }
        if (match.constrains(EmpiricalMatch.Dimension.SPECIES)) {
            where.add("species IS NOT DISTINCT FROM :species");
        }
        if (match.constrains(EmpiricalMatch.Dimension.SEASON)) {
            where.add("season_bucket IS NOT DISTINCT FROM :seasonBucket");
        }
        if (match.constrains(EmpiricalMatch.Dimension.TIME)) {
            where.add("time_bucket IS NOT DISTINCT FROM :timeBucket");
        }
        if (match.constrains(EmpiricalMatch.Dimension.STRUCTURE)) {
            where.add("structure IS NOT DISTINCT FROM :structure");
        }
        if (match.constrains(EmpiricalMatch.Dimension.WIND)) {
            where.add("wind_bucket IS NOT DISTINCT FROM :windBucket");
        }
        if (match.constrains(EmpiricalMatch.Dimension.WIND_DIRECTION)) {
            where.add("wind_direction_bucket IS NOT DISTINCT FROM :windDirectionBucket");
        }
        if (match.constrains(EmpiricalMatch.Dimension.LURE)) {
            where.add("lure_family IS NOT DISTINCT FROM :lureFamily");
        }
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(fishing_effort_seconds), 0),
                       COALESCE(SUM(bite_count), 0),
                       COALESCE(SUM(fish_on_count), 0),
                       COALESCE(SUM(landed_count), 0),
                       COALESCE(SUM(contributing_session_waypoint_count), 0)
                FROM historical_performance_contributions
                WHERE %s
                """.formatted(where));
        query.setParameter("algorithmVersion", match.algorithmVersion());
        if (match.constrains(EmpiricalMatch.Dimension.LAKE)) {
            query.setParameter("lakeId", match.lakeId());
        }
        if (match.constrains(EmpiricalMatch.Dimension.ZONE)) {
            query.setParameter("zoneId", match.zoneId());
        }
        if (match.constrains(EmpiricalMatch.Dimension.WAYPOINT)) {
            query.setParameter("tripWaypointId", match.tripWaypointId());
        }
        if (match.constrains(EmpiricalMatch.Dimension.SPECIES)) {
            query.setParameter("species", EmpiricalSql.name(match.species()));
        }
        if (match.constrains(EmpiricalMatch.Dimension.SEASON)) {
            query.setParameter("seasonBucket", EmpiricalSql.name(match.seasonBucket()));
        }
        if (match.constrains(EmpiricalMatch.Dimension.TIME)) {
            query.setParameter("timeBucket", EmpiricalSql.name(match.timeBucket()));
        }
        if (match.constrains(EmpiricalMatch.Dimension.STRUCTURE)) {
            query.setParameter("structure", EmpiricalSql.name(match.structure()));
        }
        if (match.constrains(EmpiricalMatch.Dimension.WIND)) {
            query.setParameter("windBucket", EmpiricalSql.name(match.windBucket()));
        }
        if (match.constrains(EmpiricalMatch.Dimension.WIND_DIRECTION)) {
            query.setParameter("windDirectionBucket", EmpiricalSql.name(match.windDirectionBucket()));
        }
        if (match.constrains(EmpiricalMatch.Dimension.LURE)) {
            query.setParameter("lureFamily", EmpiricalSql.name(match.lureFamily()));
        }
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
