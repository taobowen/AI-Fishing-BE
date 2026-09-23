package com.aifishing.guidance.learning;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class OutcomeAttributionReads {

    private final JdbcTemplate jdbcTemplate;

    public OutcomeAttributionReads(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FollowedOutcomeClip> findFollowed(UUID sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        SELECT id, attribution_dimension, outcome_kind, followed_recommendation
                        FROM outcome_attributions
                        WHERE fishing_session_id = ?
                          AND followed_recommendation = TRUE
                        ORDER BY attributed_at DESC
                        LIMIT 8
                        """,
                (rs, rowNum) -> new FollowedOutcomeClip(
                        rs.getObject("id", UUID.class),
                        rs.getString("attribution_dimension"),
                        rs.getString("outcome_kind"),
                        rs.getBoolean("followed_recommendation")
                ),
                sessionId
        );
    }
}
