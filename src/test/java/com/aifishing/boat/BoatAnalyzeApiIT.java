package com.aifishing.boat;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.boat.capability.AiBoatCapabilityEstimate;
import com.aifishing.boat.capability.BoatCapabilityPriors;
import com.aifishing.boat.capability.BoatCapabilityReasoner;
import com.aifishing.common.enums.WindWaveCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BoatAnalyzeApiIT extends AbstractIntegrationTest {

    @MockitoBean
    BoatCapabilityReasoner reasoner;

    @Autowired
    com.aifishing.boat.repo.BoatRepository boats;

    @BeforeEach
    void stubReasoner() {
        when(reasoner.estimate(any(), anyList(), nullable(BoatCapabilityPriors.class)))
                .thenReturn(aiResult(40, 99.0, WindWaveCapability.HIGH));
        when(reasoner.estimate(any(), anyList())).thenReturn(aiResult(40, 99.0, WindWaveCapability.HIGH));
    }

    @Test
    void numbersOnlyPatchSavesMetricsWithoutAi() throws Exception {
        String id = createBoatWithMetrics();
        reset(reasoner);
        stubReasoner();

        mockMvc.perform(asDev(patch("/api/v1/me/boats/" + id)).content("""
                        {
                          "measuredCruiseSpeedKmh": 16,
                          "comfortableRoundTripRangeKm": 20,
                          "windWaveOverride": "MEDIUM"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh").value(16))
                .andExpect(jsonPath("$.comfortableRoundTripRangeKm").value(20))
                .andExpect(jsonPath("$.windWaveOverride").value("MEDIUM"))
                .andExpect(jsonPath("$.configurationDescription").value("12ft jon 6hp"));

        verify(reasoner, never()).estimate(any(), anyList(), nullable(BoatCapabilityPriors.class));
        verify(reasoner, never()).estimate(any(), anyList());
    }

    @Test
    void descriptionChangeDoesNotSilentlyOverwriteSavedMetrics() throws Exception {
        String id = createBoatWithMetrics();

        mockMvc.perform(asDev(patch("/api/v1/me/boats/" + id)).content("""
                        {
                          "configurationDescription": "24V 100Ah LiFePO4 added to the 12ft jon"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurationDescription").value("24V 100Ah LiFePO4 added to the 12ft jon"))
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh").value(12))
                .andExpect(jsonPath("$.comfortableRoundTripRangeKm").value(18))
                .andExpect(jsonPath("$.windWaveOverride").value("LOW"));

        mockMvc.perform(asDev(get("/api/v1/me/boats/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh").value(12))
                .andExpect(jsonPath("$.comfortableRoundTripRangeKm").value(18))
                .andExpect(jsonPath("$.windWaveOverride").value("LOW"));
    }

    @Test
    void analyzeReturnsProposalUsingSavedMetricsAndDoesNotWriteBoat() throws Exception {
        String id = createBoatWithMetrics();
        reset(reasoner);
        ArgumentCaptor<BoatCapabilityPriors> priors = ArgumentCaptor.forClass(BoatCapabilityPriors.class);
        when(reasoner.estimate(any(), anyList(), priors.capture())).thenReturn(aiResult(14, 28.0, WindWaveCapability.MEDIUM));
        when(reasoner.estimate(any(), anyList())).thenReturn(aiResult(14, 28.0, WindWaveCapability.MEDIUM));

        mockMvc.perform(asDev(post("/api/v1/me/boats/analyze")).content("""
                        {
                          "configurationDescription": "24V 100Ah LiFePO4 added to the 12ft jon",
                          "savedCruiseSpeedKmh": 12,
                          "savedPracticalRangeKm": 18,
                          "savedWindWaveCapability": "LOW"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposal.cruiseSpeedKmh").value(14))
                .andExpect(jsonPath("$.proposal.practicalRangeKm").value(28))
                .andExpect(jsonPath("$.proposal.windWaveCapability").value("MEDIUM"))
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh").doesNotExist());

        verify(reasoner, atLeastOnce()).estimate(any(), anyList(), nullable(BoatCapabilityPriors.class));
        assertThat(priors.getAllValues()).isNotEmpty();
        BoatCapabilityPriors used = priors.getAllValues().getLast();
        assertThat(used).isNotNull();
        assertThat(used.cruiseSpeedKmh()).isEqualTo(12.0);
        assertThat(used.practicalRangeKm()).isEqualTo(18.0);
        assertThat(used.windWaveCapability()).isEqualTo(WindWaveCapability.LOW);

        mockMvc.perform(asDev(get("/api/v1/me/boats/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh").value(12))
                .andExpect(jsonPath("$.comfortableRoundTripRangeKm").value(18))
                .andExpect(jsonPath("$.windWaveOverride").value("LOW"));
    }

    @Test
    void userEditedProposalIsWhatSavePersists() throws Exception {
        String id = createBoatWithMetrics();

        mockMvc.perform(asDev(patch("/api/v1/me/boats/" + id)).content("""
                        {
                          "configurationDescription": "24V 100Ah LiFePO4 added to the 12ft jon",
                          "measuredCruiseSpeedKmh": 15,
                          "comfortableRoundTripRangeKm": 28,
                          "windWaveOverride": "MEDIUM"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh").value(15))
                .andExpect(jsonPath("$.comfortableRoundTripRangeKm").value(28))
                .andExpect(jsonPath("$.windWaveOverride").value("MEDIUM"));
    }

    @Test
    void analyzeRequiresDescription() throws Exception {
        mockMvc.perform(asDev(post("/api/v1/me/boats/analyze")).content("""
                        {
                          "savedCruiseSpeedKmh": 12
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyzeDoesNotCreateOrMutateABoatRow() throws Exception {
        long before = boats.count();
        mockMvc.perform(asDev(post("/api/v1/me/boats/analyze")).content("""
                        {
                          "configurationDescription": "16ft aluminum bass boat 60hp"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposal").exists())
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
        assertThat(boats.count()).isEqualTo(before);
    }

    private String createBoatWithMetrics() throws Exception {
        MvcResult created = mockMvc.perform(asDev(post("/api/v1/me/boats")).content("""
                        {
                          "name": "Jon",
                          "configurationDescription": "12ft jon 6hp",
                          "measuredCruiseSpeedKmh": 12,
                          "comfortableRoundTripRangeKm": 18,
                          "windWaveOverride": "LOW"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.measuredCruiseSpeedKmh").value(12))
                .andExpect(jsonPath("$.comfortableRoundTripRangeKm").value(18))
                .andExpect(jsonPath("$.windWaveOverride").value("LOW"))
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    private static BoatCapabilityReasoner.Result aiResult(
            double cruise,
            Double range,
            WindWaveCapability wind
    ) {
        return new BoatCapabilityReasoner.Result(
                new AiBoatCapabilityEstimate(
                        new AiBoatCapabilityEstimate.MetricNumber(cruise, 0.8),
                        new AiBoatCapabilityEstimate.MetricNumber(range, 0.8),
                        new AiBoatCapabilityEstimate.MetricEnum(wind, 0.7),
                        new AiBoatCapabilityEstimate.Reasoning("speed", "range", "wind"),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        false
                ),
                false,
                "gpt-test",
                Map.of(),
                Map.of()
        );
    }
}
