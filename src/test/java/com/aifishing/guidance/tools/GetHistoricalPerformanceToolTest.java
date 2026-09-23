package com.aifishing.guidance.tools;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.HistoricalPerformance;
import com.aifishing.guidance.contracts.SeasonBucket;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.aifishing.guidance.empirical.EmpiricalAlgorithm;
import com.aifishing.guidance.empirical.EmpiricalMatch;
import com.aifishing.guidance.empirical.EmpiricalRawCounts;
import com.aifishing.guidance.empirical.HistoricalContributionStore;
import com.aifishing.guidance.empirical.HistoricalPerformanceQuery;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetHistoricalPerformanceToolTest {

    private static final Instant NOW = Instant.parse("2026-09-16T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID LAKE = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID WAYPOINT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private HistoricalContributionStore contributionStore;
    @Mock
    private LakeRepository lakeRepository;
    @Mock
    private TripWaypointRepository tripWaypointRepository;

    private GetHistoricalPerformanceTool tool;

    @BeforeEach
    void setUp() {
        HistoricalPerformanceQuery query = new HistoricalPerformanceQuery(
                contributionStore, lakeRepository, tripWaypointRepository, new GuidanceProperties(), CLOCK);
        tool = new GetHistoricalPerformanceTool(query, CLOCK);
    }

    @Test
    void emptyLookupIsUnknown() {
        when(lakeRepository.findById(LAKE)).thenReturn(Optional.of(lake()));
        when(tripWaypointRepository.findById(WAYPOINT)).thenReturn(Optional.empty());
        when(contributionStore.sumMatching(any())).thenReturn(EmpiricalRawCounts.ZERO);

        ToolResultEnvelope result = tool.execute(request(WAYPOINT));

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNKNOWN);
        assertThat(result.data()).isNull();
    }

    @Test
    void errorIsUnknown() {
        when(lakeRepository.findById(LAKE)).thenThrow(new IllegalStateException("db down"));

        ToolResultEnvelope result = tool.execute(request(WAYPOINT));

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNKNOWN);
        assertThat(result.errorType()).isNull();
    }

    @Test
    void backoffSumsRawThenRecomputesAndNeverAveragesScores() {
        when(lakeRepository.findById(LAKE)).thenReturn(Optional.of(lake()));
        when(tripWaypointRepository.findById(WAYPOINT)).thenReturn(Optional.empty());
        EmpiricalRawCounts high = new EmpiricalRawCounts(3600, 4, 4, 0, 1);
        EmpiricalRawCounts low = new EmpiricalRawCounts(36000, 0, 0, 0, 1);
        EmpiricalRawCounts summed = high.plus(low);
        AtomicReference<EmpiricalMatch> captured = new AtomicReference<>();
        when(contributionStore.sumMatching(any())).thenAnswer(invocation -> {
            EmpiricalMatch match = invocation.getArgument(0);
            captured.set(match);
            if (match.constrains(EmpiricalMatch.Dimension.WAYPOINT)) {
                return EmpiricalRawCounts.ZERO;
            }
            return summed;
        });

        ToolResultEnvelope result = tool.execute(request(WAYPOINT));

        assertThat(result.status()).isEqualTo(ToolResultStatus.OK);
        assertThat(result.data().path("biteCount").asInt()).isEqualTo(4);
        assertThat(result.data().path("fishOnCount").asInt()).isEqualTo(4);
        assertThat(result.data().path("landedCount").asInt()).isEqualTo(0);
        assertThat(result.data().path("catchCount").asInt()).isEqualTo(0);
        assertThat(result.data().path("fishingEffortSeconds").asLong()).isEqualTo(39600);
        assertThat(result.data().path("empiricalAlgorithmVersion").asInt()).isEqualTo(EmpiricalAlgorithm.VERSION);
        assertThat(result.data().path("seasonBucket").asText()).isEqualTo(SeasonBucket.FALL.name());
        assertThat(result.data().has("windDirectionBucket")).isFalse();
        EmpiricalAlgorithm.Derived expected = EmpiricalAlgorithm.derive(summed, new GuidanceProperties().getEmpirical());
        assertThat(result.data().path("smoothedScore").asDouble()).isEqualTo(expected.smoothedScore());
        EmpiricalAlgorithm.Derived averaged = averageScores(high, low);
        assertThat(result.data().path("smoothedScore").asDouble()).isNotEqualTo(averaged.smoothedScore());
        assertThat(captured.get().constrains(EmpiricalMatch.Dimension.WAYPOINT)).isFalse();
        HistoricalPerformance parsed = GuidanceContracts.mapper().convertValue(result.data(), HistoricalPerformance.class);
        assertThat(parsed.fishOnCount()).isEqualTo(4);
        assertThat(parsed.landedCount()).isEqualTo(0);
    }

    private static EmpiricalAlgorithm.Derived averageScores(EmpiricalRawCounts left, EmpiricalRawCounts right) {
        GuidanceProperties.Empirical cfg = new GuidanceProperties().getEmpirical();
        EmpiricalAlgorithm.Derived a = EmpiricalAlgorithm.derive(left, cfg);
        EmpiricalAlgorithm.Derived b = EmpiricalAlgorithm.derive(right, cfg);
        return new EmpiricalAlgorithm.Derived(
                (a.effortMinutes() + b.effortMinutes()) / 2.0,
                null,
                null,
                null,
                (a.cpue() + b.cpue()) / 2.0,
                (a.smoothedScore() + b.smoothedScore()) / 2.0,
                (a.sampleConfidence() + b.sampleConfidence()) / 2.0
        );
    }

    private static ToolRequestEnvelope request(UUID waypointId) {
        ObjectNode args = GuidanceContracts.mapper().createObjectNode();
        args.put("lakeId", LAKE.toString());
        args.put("tripWaypointId", waypointId.toString());
        args.put("targetSpecies", FishSpecies.SMALLMOUTH_BASS.name());
        return new ToolRequestEnvelope(GuidanceSchemaVersion.VALUE, ToolName.GET_HISTORICAL_PERFORMANCE, args, NOW);
    }

    private static Lake lake() {
        Lake lake = new Lake();
        lake.setId(LAKE);
        lake.setTimeZoneId("America/Toronto");
        return lake;
    }
}
