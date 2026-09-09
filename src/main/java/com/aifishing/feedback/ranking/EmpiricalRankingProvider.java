package com.aifishing.feedback.ranking;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.performance.ShrinkageScorer;
import com.aifishing.planning.candidate.CandidateSpot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class EmpiricalRankingProvider {

    private final JdbcTemplate jdbcTemplate;
    private final FeedbackProperties feedbackProperties;

    public EmpiricalRankingProvider(JdbcTemplate jdbcTemplate, FeedbackProperties feedbackProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.feedbackProperties = feedbackProperties;
    }

    public Map<UUID, EmpiricalEvidence> scoreCandidates(
            UUID lakeId,
            FishSpecies primarySpecies,
            UUID userId,
            List<CandidateSpot> spots
    ) {
        Map<UUID, EmpiricalEvidence> out = new HashMap<>();
        if (spots == null || spots.isEmpty() || lakeId == null || primarySpecies == null) {
            return out;
        }
        List<UUID> ids = spots.stream()
                .map(CandidateSpot::getFeatureId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return out;
        }
        double bufferM = feedbackProperties.getPerformance().getSpatialBufferM();
        String inList = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String effortSql = """
                WITH candidates AS (
                    SELECT id AS feature_id, geometry
                    FROM lake_features
                    WHERE id IN (%s)
                )
                SELECT c.feature_id,
                       fs.user_id,
                       COALESCE(SUM(
                           CASE
                               WHEN e.track_geometry IS NOT NULL AND ST_NPoints(e.track_geometry) >= 2
                                    AND ST_Length(ST_Transform(e.track_geometry, 3857)) > 0
                               THEN e.duration_seconds * (
                                    COALESCE(ST_Length(ST_Intersection(
                                        ST_Transform(e.track_geometry, 3857),
                                        ST_Buffer(ST_Transform(c.geometry, 3857), ?)
                                    )), 0) / ST_Length(ST_Transform(e.track_geometry, 3857))
                               )
                               WHEN e.representative_location IS NOT NULL
                                    AND ST_DWithin(
                                        e.representative_location::geography,
                                        c.geometry::geography,
                                        ?
                                    )
                               THEN e.duration_seconds
                               ELSE 0
                           END
                       ), 0) AS effort_seconds
                FROM candidates c
                JOIN fishing_effort_segments e ON e.segment_type = 'FISHING'
                JOIN fishing_sessions fs ON fs.id = e.fishing_session_id AND fs.status = 'COMPLETED'
                JOIN trips t ON t.id = fs.trip_id
                WHERE t.lake_id = ?
                  AND t.primary_target_species = ?
                GROUP BY c.feature_id, fs.user_id
                """.formatted(inList);
        String catchSql = """
                WITH candidates AS (
                    SELECT id AS feature_id, geometry
                    FROM lake_features
                    WHERE id IN (%s)
                )
                SELECT c.feature_id,
                       fs.user_id,
                       COUNT(*) FILTER (WHERE ce.outcome = 'LANDED') AS landed
                FROM candidates c
                JOIN catch_events ce
                  ON ce.status = 'ACTIVE'
                 AND ce.location IS NOT NULL
                 AND ST_DWithin(ce.location::geography, c.geometry::geography, ?)
                JOIN fishing_sessions fs ON fs.id = ce.fishing_session_id AND fs.status = 'COMPLETED'
                JOIN trips t ON t.id = fs.trip_id
                WHERE t.lake_id = ?
                  AND t.primary_target_species = ?
                GROUP BY c.feature_id, fs.user_id
                """.formatted(inList);

        Map<UUID, Map<UUID, Double>> effortHoursByFeatureUser = new HashMap<>();
        jdbcTemplate.query(effortSql, ps -> {
            int i = 1;
            for (UUID id : ids) {
                ps.setObject(i++, id);
            }
            ps.setDouble(i++, bufferM);
            ps.setDouble(i++, bufferM);
            ps.setObject(i++, lakeId);
            ps.setString(i, primarySpecies.name());
        }, rs -> {
            UUID featureId = rs.getObject("feature_id", UUID.class);
            UUID owner = rs.getObject("user_id", UUID.class);
            double hours = rs.getDouble("effort_seconds") / 3600.0;
            effortHoursByFeatureUser
                    .computeIfAbsent(featureId, key -> new HashMap<>())
                    .merge(owner, hours, Double::sum);
        });
        Map<UUID, Map<UUID, Double>> landedByFeatureUser = new HashMap<>();
        jdbcTemplate.query(catchSql, ps -> {
            int i = 1;
            for (UUID id : ids) {
                ps.setObject(i++, id);
            }
            ps.setDouble(i++, bufferM);
            ps.setObject(i++, lakeId);
            ps.setString(i, primarySpecies.name());
        }, rs -> {
            UUID featureId = rs.getObject("feature_id", UUID.class);
            UUID owner = rs.getObject("user_id", UUID.class);
            double landed = rs.getDouble("landed");
            landedByFeatureUser
                    .computeIfAbsent(featureId, key -> new HashMap<>())
                    .merge(owner, landed, Double::sum);
        });

        FeedbackProperties.Performance cfg = feedbackProperties.getPerformance();
        for (UUID featureId : ids) {
            Map<UUID, Double> effort = effortHoursByFeatureUser.getOrDefault(featureId, Map.of());
            Map<UUID, Double> landed = landedByFeatureUser.getOrDefault(featureId, Map.of());
            double userHours = effort.getOrDefault(userId, 0.0);
            double userLanded = landed.getOrDefault(userId, 0.0);
            double globalHours = effort.values().stream().mapToDouble(Double::doubleValue).sum();
            double globalLanded = landed.values().stream().mapToDouble(Double::doubleValue).sum();
            ShrinkageScorer.Result personal = ShrinkageScorer.score(userLanded, userHours, cfg);
            ShrinkageScorer.Result global = ShrinkageScorer.score(globalLanded, globalHours, cfg);
            ShrinkageScorer.Result blended = ShrinkageScorer.blend(
                    personal, global, userHours, cfg.getUserBlendPriorHours());
            out.put(featureId, new EmpiricalEvidence(blended.historicalPerformance(), blended.evidenceConfidence()));
        }
        for (CandidateSpot spot : spots) {
            if (spot.getTargetKind() == com.aifishing.planning.spatial.TargetKind.ZONE && spot.getZoneId() != null) {
                EmpiricalEvidence zone = scorePairedKey("zone_id", spot.getZoneId(), lakeId, primarySpecies, userId);
                if (zone.hasSignal()) {
                    out.put(spot.getZoneId(), zone);
                }
            } else if (spot.getFishingTargetId() != null) {
                EmpiricalEvidence target = scorePairedKey(
                        "fishing_target_id", spot.getFishingTargetId(), lakeId, primarySpecies, userId);
                if (target.hasSignal()) {
                    out.put(spot.getFeatureId(), target);
                }
            }
        }
        return out;
    }

    private EmpiricalEvidence scorePairedKey(
            String column,
            UUID key,
            UUID lakeId,
            FishSpecies primarySpecies,
            UUID userId
    ) {
        if (key == null || (!"zone_id".equals(column) && !"fishing_target_id".equals(column))) {
            return EmpiricalEvidence.none();
        }
        Integer effortRows = jdbcTemplate.queryForObject(
                "select count(*) from fishing_effort_segments e "
                        + "join fishing_sessions fs on fs.id = e.fishing_session_id and fs.status = 'COMPLETED' "
                        + "join trips t on t.id = fs.trip_id "
                        + "where e.segment_type = 'FISHING' and e." + column + " = ? and t.lake_id = ? and t.primary_target_species = ?",
                Integer.class,
                key,
                lakeId,
                primarySpecies.name()
        );
        if (effortRows == null || effortRows == 0) {
            return EmpiricalEvidence.none();
        }
        Integer catchRows = jdbcTemplate.queryForObject(
                "select count(*) from catch_events ce "
                        + "join fishing_sessions fs on fs.id = ce.fishing_session_id and fs.status = 'COMPLETED' "
                        + "join trips t on t.id = fs.trip_id "
                        + "where ce.status = 'ACTIVE' and ce.outcome = 'LANDED' and ce." + column + " = ? "
                        + "and t.lake_id = ? and t.primary_target_species = ?",
                Integer.class,
                key,
                lakeId,
                primarySpecies.name()
        );
        Double hours = jdbcTemplate.queryForObject(
                "select coalesce(sum(e.duration_seconds),0)/3600.0 from fishing_effort_segments e "
                        + "join fishing_sessions fs on fs.id = e.fishing_session_id and fs.status = 'COMPLETED' "
                        + "join trips t on t.id = fs.trip_id "
                        + "where e.segment_type = 'FISHING' and e." + column + " = ? and t.lake_id = ? and t.primary_target_species = ?",
                Double.class,
                key,
                lakeId,
                primarySpecies.name()
        );
        FeedbackProperties.Performance cfg = feedbackProperties.getPerformance();
        ShrinkageScorer.Result global = ShrinkageScorer.score(
                catchRows == null ? 0 : catchRows.doubleValue(),
                hours == null ? 0 : hours,
                cfg
        );
        return new EmpiricalEvidence(global.historicalPerformance(), global.evidenceConfidence());
    }
}
