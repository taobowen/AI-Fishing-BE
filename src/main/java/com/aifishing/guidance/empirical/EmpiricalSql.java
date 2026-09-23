package com.aifishing.guidance.empirical;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.guidance.contracts.SeasonBucket;
import com.aifishing.guidance.contracts.TimeBucket;
import com.aifishing.guidance.contracts.WindBucket;
import com.aifishing.guidance.contracts.WindDirectionBucket;
import com.aifishing.lake.processing.dto.FeatureType;
import jakarta.persistence.Query;

import java.util.Locale;
import java.util.UUID;

final class EmpiricalSql {

    static final String GRAIN_PREDICATE = """
            lake_id IS NOT DISTINCT FROM :lakeId
            AND zone_id IS NOT DISTINCT FROM :zoneId
            AND trip_waypoint_id IS NOT DISTINCT FROM :tripWaypointId
            AND species IS NOT DISTINCT FROM :species
            AND season_bucket IS NOT DISTINCT FROM :seasonBucket
            AND time_bucket IS NOT DISTINCT FROM :timeBucket
            AND structure IS NOT DISTINCT FROM :structure
            AND wind_bucket IS NOT DISTINCT FROM :windBucket
            AND wind_direction_bucket IS NOT DISTINCT FROM :windDirectionBucket
            AND lure_family IS NOT DISTINCT FROM :lureFamily
            AND empirical_algorithm_version = :algorithmVersion
            """;

    static final String GRAIN_ORDER = """
            lake_id NULLS FIRST, zone_id NULLS FIRST, trip_waypoint_id NULLS FIRST,
            species NULLS FIRST, season_bucket NULLS FIRST, time_bucket NULLS FIRST,
            structure NULLS FIRST, wind_bucket NULLS FIRST, wind_direction_bucket NULLS FIRST,
            lure_family NULLS FIRST, empirical_algorithm_version
            """;

    private EmpiricalSql() {
    }

    static void bindGrain(Query query, EmpiricalGrain grain) {
        query.setParameter("lakeId", grain.lakeId());
        query.setParameter("zoneId", grain.zoneId());
        query.setParameter("tripWaypointId", grain.tripWaypointId());
        query.setParameter("species", name(grain.species()));
        query.setParameter("seasonBucket", name(grain.seasonBucket()));
        query.setParameter("timeBucket", name(grain.timeBucket()));
        query.setParameter("structure", name(grain.structure()));
        query.setParameter("windBucket", name(grain.windBucket()));
        query.setParameter("windDirectionBucket", name(grain.windDirectionBucket()));
        query.setParameter("lureFamily", name(grain.lureFamily()));
        query.setParameter("algorithmVersion", grain.algorithmVersion());
    }

    static EmpiricalGrain grain(
            Object lakeId,
            Object zoneId,
            Object tripWaypointId,
            Object species,
            Object seasonBucket,
            Object timeBucket,
            Object structure,
            Object windBucket,
            Object windDirectionBucket,
            Object lureFamily,
            Object version
    ) {
        return new EmpiricalGrain(
                uuid(lakeId),
                uuid(zoneId),
                uuid(tripWaypointId),
                parse(FishSpecies.class, species),
                parse(SeasonBucket.class, seasonBucket),
                parse(TimeBucket.class, timeBucket),
                parse(FeatureType.class, structure),
                parse(WindBucket.class, windBucket),
                parse(WindDirectionBucket.class, windDirectionBucket),
                parse(LureFamily.class, lureFamily),
                asInt(version)
        );
    }

    static UUID uuid(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(raw));
    }

    static int asInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return raw == null ? 0 : Integer.parseInt(String.valueOf(raw));
    }

    static long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        return raw == null ? 0L : Long.parseLong(String.valueOf(raw));
    }

    static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    static <E extends Enum<E>> E parse(Class<E> type, Object raw) {
        if (raw == null) {
            return null;
        }
        String name = String.valueOf(raw).trim();
        if (name.isEmpty()) {
            return null;
        }
        return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
    }
}
