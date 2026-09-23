package com.aifishing.planning.candidate;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SimcoeSnapshotMembershipAuditTest {

    private static final UUID SNAPSHOT = UUID.fromString("777a0073-7a01-4988-978a-ede4946d3cf0");
    private static final String JDBC = "jdbc:postgresql://127.0.0.1:5433/aifishing";

    @Test
    void liveSimcoeUnassignedIsMostlyPolicyLeftoverNotCompressorHiddenGap() throws Exception {
        assumeTrue(canConnect(), "live PostGIS on 5433 not available");
        try (Connection connection = DriverManager.getConnection(JDBC, "aifishing", "aifishing");
             Statement statement = connection.createStatement()) {
            ResultSet totals = statement.executeQuery("""
                    SELECT COUNT(*) AS targets,
                           COUNT(*) FILTER (WHERE m.fishing_target_id IS NOT NULL) AS assigned,
                           COUNT(*) FILTER (WHERE m.fishing_target_id IS NULL) AS unassigned
                    FROM lake_fishing_targets t
                    LEFT JOIN (
                      SELECT DISTINCT m.fishing_target_id
                      FROM lake_fishing_zone_members m
                      JOIN lake_fishing_zones z ON z.id = m.zone_id
                      WHERE z.spatial_planning_snapshot_id = '%s'
                    ) m ON m.fishing_target_id = t.id
                    WHERE t.spatial_planning_snapshot_id = '%s'
                    """.formatted(SNAPSHOT, SNAPSHOT));
            assertThat(totals.next()).isTrue();
            int targets = totals.getInt("targets");
            int assigned = totals.getInt("assigned");
            int unassigned = totals.getInt("unassigned");
            totals.close();
            assertThat(targets).isEqualTo(17058);
            assertThat(assigned + unassigned).isEqualTo(targets);
            assertThat(unassigned).isEqualTo(678);
            assertThat(assigned).isGreaterThan((int) (targets * 0.9));

            ResultSet launchB = statement.executeQuery("""
                    SELECT COUNT(*) AS n
                    FROM lake_fishing_targets t
                    WHERE t.spatial_planning_snapshot_id = '%s'
                      AND ST_DWithin(
                            t.representative_point::geography,
                            ST_SetSRID(ST_MakePoint(-79.580, 44.409), 4326)::geography,
                            2000)
                    """.formatted(SNAPSHOT));
            assertThat(launchB.next()).isTrue();
            assertThat(launchB.getInt("n")).isGreaterThan(0);
        }
    }

    private static boolean canConnect() {
        try (Connection ignored = DriverManager.getConnection(JDBC, "aifishing", "aifishing")) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
