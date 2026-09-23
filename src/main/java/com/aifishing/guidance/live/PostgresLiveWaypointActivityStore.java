package com.aifishing.guidance.live;

import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.LiveWaypointActivity;
import com.aifishing.guidance.contracts.LiveWaypointPressure;
import com.aifishing.guidance.spi.LiveWaypointActivityStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes {@code session_live_position} with geography meter semantics.
 * Never scans {@code session_location_points}.
 */
@Component
public class PostgresLiveWaypointActivityStore implements LiveWaypointActivityStore {

    static final int DEFAULT_RADIUS_METERS = 300;
    static final int POSITION_MAX_AGE_MINUTES = 10;
    static final int FISH_ON_RADIUS_METERS = 30;
    static final int FISH_ON_WINDOW_HOURS = 2;

    private static final String FIND_NEARBY = """
            SELECT
                (
                    SELECT COUNT(*)
                    FROM session_live_position slp
                    INNER JOIN fishing_sessions fs ON fs.id = slp.fishing_session_id
                    WHERE slp.activity_state = 'FISHING'
                      AND fs.status IN ('ACTIVE', 'PAUSED')
                      AND slp.recorded_at >= :minRecordedAt
                      AND ST_DWithin(slp.location, tw.location::geography, :radiusMeters)
                      AND (:excludeSessionId::uuid IS NULL OR slp.fishing_session_id <> :excludeSessionId)
                      AND slp.fishing_session_id NOT IN (
                          SELECT swp.fishing_session_id
                          FROM session_waypoint_progress swp
                          INNER JOIN fishing_sessions own ON own.id = swp.fishing_session_id
                          WHERE swp.trip_waypoint_id = tw.id
                            AND own.status IN ('ACTIVE', 'PAUSED')
                      )
                ) AS active_anglers,
                (
                    SELECT COUNT(*)
                    FROM (
                        SELECT DISTINCT COALESCE(se.fish_interaction_id, se.id)
                        FROM session_events se
                        INNER JOIN session_live_position slp ON slp.fishing_session_id = se.fishing_session_id
                        WHERE se.type = 'FISH_ON'
                          AND se.occurred_at >= :minFishOnAt
                          AND ST_DWithin(slp.location, tw.location::geography, :fishOnRadiusMeters)
                          AND (:excludeSessionId::uuid IS NULL OR slp.fishing_session_id <> :excludeSessionId)
                          AND slp.fishing_session_id NOT IN (
                              SELECT swp.fishing_session_id
                              FROM session_waypoint_progress swp
                              INNER JOIN fishing_sessions own ON own.id = swp.fishing_session_id
                              WHERE swp.trip_waypoint_id = tw.id
                                AND own.status IN ('ACTIVE', 'PAUSED')
                          )
                    ) recent_fish
                ) AS recent_fish_on
            FROM trip_waypoints tw
            WHERE tw.id = :tripWaypointId
              AND tw.location IS NOT NULL
              AND NOT ST_IsEmpty(tw.location)
            """;

    private static final String UPSERT_LOCATION = """
            INSERT INTO session_live_position (
                fishing_session_id, location, recorded_at, accuracy_m, activity_state, lake_id, updated_at
            ) VALUES (
                :sessionId,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                :recordedAt,
                :accuracyM,
                :activityState,
                :lakeId,
                now()
            )
            ON CONFLICT (fishing_session_id) DO UPDATE SET
                location = EXCLUDED.location,
                recorded_at = EXCLUDED.recorded_at,
                accuracy_m = EXCLUDED.accuracy_m,
                activity_state = EXCLUDED.activity_state,
                lake_id = COALESCE(EXCLUDED.lake_id, session_live_position.lake_id),
                updated_at = now()
            """;

    private static final String UPSERT_ACTIVITY = """
            UPDATE session_live_position
               SET activity_state = :activityState,
                   updated_at = now()
             WHERE fishing_session_id = :sessionId
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public PostgresLiveWaypointActivityStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.clock = clock;
    }

    @Override
    public Optional<LiveWaypointActivity> findNearby(UUID tripWaypointId, int radiusMeters, UUID excludeSessionId) {
        if (tripWaypointId == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tripWaypointId", tripWaypointId)
                .addValue("radiusMeters", radiusMeters)
                .addValue("fishOnRadiusMeters", FISH_ON_RADIUS_METERS)
                .addValue("minRecordedAt", Timestamp.from(now.minus(Duration.ofMinutes(POSITION_MAX_AGE_MINUTES))))
                .addValue("minFishOnAt", Timestamp.from(now.minus(Duration.ofHours(FISH_ON_WINDOW_HOURS))))
                .addValue("excludeSessionId", excludeSessionId);
        List<LiveWaypointActivity> rows = jdbc.query(FIND_NEARBY, params, (rs, rowNum) -> {
            int anglers = rs.getInt("active_anglers");
            int fishOn = rs.getInt("recent_fish_on");
            return new LiveWaypointActivity(
                    GuidanceSchemaVersion.VALUE,
                    tripWaypointId,
                    radiusMeters,
                    anglers,
                    fishOn,
                    pressure(anglers),
                    now
            );
        });
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public void upsertLocation(
            UUID sessionId,
            double latitudeWgs84,
            double longitudeWgs84,
            Instant recordedAt,
            Double accuracyM,
            FishingActivityState activityState,
            UUID lakeId
    ) {
        if (sessionId == null || recordedAt == null) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("latitude", latitudeWgs84)
                .addValue("longitude", longitudeWgs84)
                .addValue("recordedAt", Timestamp.from(recordedAt))
                .addValue("accuracyM", accuracyM)
                .addValue("activityState", (activityState == null ? FishingActivityState.UNKNOWN : activityState).name())
                .addValue("lakeId", lakeId);
        jdbc.update(UPSERT_LOCATION, params);
    }

    @Override
    public void upsertActivityState(UUID sessionId, FishingActivityState activityState) {
        if (sessionId == null || activityState == null) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("activityState", activityState.name());
        jdbc.update(UPSERT_ACTIVITY, params);
    }

    static LiveWaypointPressure pressure(int activeAnglersNearby) {
        if (activeAnglersNearby <= 0) {
            return LiveWaypointPressure.LOW;
        }
        if (activeAnglersNearby <= 2) {
            return LiveWaypointPressure.MODERATE;
        }
        return LiveWaypointPressure.HIGH;
    }
}
