package com.aifishing;

import com.aifishing.auth.DevAuthenticationFilter;
import com.aifishing.boat.repo.BoatRepository;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.fishingprofile.repo.FishingProfileRepository;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.gear.repo.GearRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.seed.ValidationCatalogService;
import com.aifishing.trip.repo.TripRepository;
import com.aifishing.user.domain.User;
import com.aifishing.user.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected FishingProfileRepository fishingProfileRepository;

    @Autowired
    protected GearRepository gearRepository;

    @Autowired
    protected BoatRepository boatRepository;

    @Autowired
    protected LakeRepository lakeRepository;

    @Autowired
    protected TripRepository tripRepository;

    @Autowired
    protected TripPlanRepository tripPlanRepository;

    @Autowired
    protected TripWaypointRepository tripWaypointRepository;

    @Autowired
    protected FishingSessionRepository fishingSessionRepository;

    @Autowired
    protected GeoMapper geoMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected com.aifishing.planning.spatial.SpatialSnapshotJob spatialSnapshotJob;

    protected void ensureSpatialSnapshot(java.util.UUID lakeId) {
        ensureSpatialSnapshot(
                lakeId,
                com.aifishing.lake.processing.dto.Pipeline.GIS,
                com.aifishing.planning.PlanningFixtures.ANALYSIS_VERSION);
    }

    protected void ensureSpatialSnapshot(
            java.util.UUID lakeId,
            com.aifishing.lake.processing.dto.Pipeline pipeline,
            String analysisVersion
    ) {
        spatialSnapshotJob.build(lakeId, pipeline, analysisVersion);
    }

    @BeforeEach
    void resetData() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    guidance_eval_case_results,
                    guidance_eval_runs,
                    guidance_online_metric_rollups,
                    guidance_learning_outbox,
                    outcome_attributions,
                    session_live_position,
                    historical_performance_contributions,
                    historical_performance,
                    session_summaries,
                    agent_reflections,
                    semantic_memories,
                    inferred_user_preferences,
                    user_fishing_preferences,
                    user_action_events,
                    guidance_trigger_outbox,
                    agent_feedback,
                    guidance_plan_steps,
                    guidance_plan_versions,
                    agent_delivered_decisions,
                    agent_validations,
                    agent_candidate_decisions,
                    agent_tool_calls,
                    agent_runs,
                    lure_events,
                    weather_snapshots,
                    session_events,
                    web_plan_quota_ledger,
                    web_plan_generation_requests,
                    user_web_plan_entitlements,
                    trip_stop_subtargets,
                    lake_navigation_tiles,
                    lake_fishing_water_paths,
                    lake_fishing_nav_edges,
                    lake_fishing_nav_nodes,
                    lake_fishing_zone_portals,
                    lake_fishing_target_samples,
                    lake_fishing_zone_members,
                    lake_fishing_zones,
                    lake_fishing_targets,
                    spatial_planning_snapshots,
                    lake_features,
                    derived_analysis_artifacts,
                    lake_feature_status,
                    lake_analysis_runs,
                    fishing_restrictions,
                    fish_habitats,
                    fish_stocking_records,
                    lake_fish_species,
                    lake_access_points,
                    wetlands,
                    lake_waterways,
                    bathymetry_points,
                    bathymetry_contours,
                    lake_boundaries,
                    raw_data_objects,
                    lake_dataset_status,
                    lake_ops_jobs,
                    product_analytics_events,
                    catch_photos,
                    catch_events,
                    session_ad_hoc_fishing_stops,
                    fishing_effort_segments,
                    session_pause_intervals,
                    session_client_events,
                    session_location_points,
                    session_waypoint_progress,
                    fishing_sessions,
                    trip_plan_transit_legs,
                    trip_waypoints,
                    trip_plans,
                    trip_planning_input_targets,
                    trip_planning_input_snapshots,
                    trip_planning_runs,
                    trip_strategy_runs,
                    trip_required_points,
                    trip_launch_selections,
                    trips,
                    fishing_template_targets,
                    fishing_templates,
                    gear,
                    boats,
                    boat_capability_profiles,
                    fishing_profiles,
                    lakes,
                    users
                RESTART IDENTITY CASCADE
                """);
        jdbcTemplate.update("""
                UPDATE agent_runtime_control
                SET agent_enabled = TRUE,
                    production_version = 'v1',
                    candidate_version = NULL,
                    shadow_enabled = FALSE,
                    learning_enabled = TRUE,
                    updated_at = now()
                WHERE id = 1
                """);
        entityManager.clear();

        User dev = new User();
        dev.setId(DevSeedIds.USER_ID);
        dev.setEmail("dev@aifishing.local");
        dev.setDisplayName("Dev Angler");
        userRepository.save(dev);

        User other = new User();
        other.setId(DevSeedIds.OTHER_USER_ID);
        other.setEmail("other@aifishing.local");
        other.setDisplayName("Other Angler");
        userRepository.save(other);

        seedLake(DevSeedIds.LAKE_ID, "Head Lake", 44.75, -78.92);
    }

    protected Lake seedLake(java.util.UUID id, String name, double lat, double lng) {
        Lake lake = new Lake();
        lake.setId(id);
        lake.setName(name);
        lake.setProvince("Ontario");
        lake.setCountry("Canada");
        lake.setSource("MANUAL_SEED");
        lake.setCentroid(geoMapper.toPoint(new GeoPointDto(lat, lng)));
        lake.setTimeZoneId("America/Toronto");
        ValidationCatalogService.VALIDATION_LAKES.stream()
                .filter(spec -> spec.id().equals(id))
                .findFirst()
                .ifPresent(spec -> lake.setCardImagePath(spec.cardImagePath()));
        return lakeRepository.save(lake);
    }

    protected MockHttpServletRequestBuilder asDev(MockHttpServletRequestBuilder builder) {
        return builder
                .header(DevAuthenticationFilter.USER_ID_HEADER, DevSeedIds.USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
    }

    protected MockHttpServletRequestBuilder asWeb(MockHttpServletRequestBuilder builder) {
        return asDev(builder).header(DevAuthenticationFilter.AUTH_CLIENT_HEADER, "WEB");
    }

    protected MockHttpServletRequestBuilder asOther(MockHttpServletRequestBuilder builder) {
        return builder
                .header(DevAuthenticationFilter.USER_ID_HEADER, DevSeedIds.OTHER_USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
    }

    protected JsonNode awaitLakeOpsJob(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode job = objectMapper.readTree(body);
        String jobId = job.path("jobId").asText();
        String status = job.path("status").asText();
        if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
            return job;
        }
        for (int i = 0; i < 3000; i++) {
            String latestBody = mockMvc.perform(asDev(get("/api/v1/admin/lakes/jobs/" + jobId)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode latest = objectMapper.readTree(latestBody);
            String latestStatus = latest.path("status").asText();
            if ("SUCCEEDED".equals(latestStatus) || "FAILED".equals(latestStatus)) {
                return latest;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("lake ops job did not finish: " + jobId);
    }

    protected JsonNode awaitLakeOpsJobResult(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode job = awaitLakeOpsJob(request);
        assertThat(job.path("status").asText())
                .withFailMessage("lake ops job failed: %s", job.path("errorMessage").asText())
                .isEqualTo("SUCCEEDED");
        return job.path("result");
    }
}
