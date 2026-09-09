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
import com.aifishing.trip.repo.TripRepository;
import com.aifishing.user.domain.User;
import com.aifishing.user.repo.UserRepository;
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
                    catch_photos,
                    catch_events,
                    fishing_effort_segments,
                    session_pause_intervals,
                    session_client_events,
                    session_location_points,
                    session_waypoint_progress,
                    fishing_sessions,
                    trip_launch_selections,
                    trip_plan_transit_legs,
                    trip_waypoints,
                    trip_plans,
                    trip_planning_runs,
                    trip_strategy_runs,
                    trips,
                    gear,
                    boats,
                    boat_capability_profiles,
                    fishing_profiles,
                    lakes,
                    users
                RESTART IDENTITY CASCADE
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

        Lake lake = new Lake();
        lake.setId(DevSeedIds.LAKE_ID);
        lake.setName("Head Lake");
        lake.setProvince("Ontario");
        lake.setCountry("Canada");
        lake.setSource("MANUAL_SEED");
        lake.setCentroid(geoMapper.toPoint(new GeoPointDto(44.75, -78.92)));
        lake.setTimeZoneId("America/Toronto");
        lakeRepository.save(lake);
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
}
