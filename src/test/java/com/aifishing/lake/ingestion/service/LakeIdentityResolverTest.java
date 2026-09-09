package com.aifishing.lake.ingestion.service;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.OntarioProperties;
import com.aifishing.lake.ingestion.OntarioFixtures;
import com.aifishing.lake.ingestion.dto.FeatureQuery;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.processor.CrsTransformer;
import com.aifishing.lake.ingestion.processor.GeoJsonFeatureParser;
import com.aifishing.lake.ingestion.source.OntarioFeatureClient;
import com.aifishing.lake.repo.LakeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LakeIdentityResolverTest {

    @Mock
    private OntarioFeatureClient featureClient;

    @Mock
    private LakeRepository lakeRepository;

    private LakeIdentityResolver resolver;
    private final GeoMapper geoMapper = new GeoMapper();

    @BeforeEach
    void setUp() {
        OntarioProperties properties = new OntarioProperties();
        properties.getLio().setOpen01Base("https://example.test/LIO_Open01/MapServer");
        properties.getLio().setWaterbodyLayer(25);
        resolver = new LakeIdentityResolver(
                properties,
                featureClient,
                new GeoJsonFeatureParser(new ObjectMapper()),
                new CrsTransformer(),
                lakeRepository
        );
        lenient().when(lakeRepository.save(any(Lake.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void disambiguatesDuplicateNamesUsingCentroid() {
        Lake lake = lake("Rice Lake", 44.18, -78.17);
        when(featureClient.query(any(FeatureQuery.class))).thenReturn(List.of(new RawPage(
                1,
                OntarioFixtures.twoRiceLakes().getBytes(StandardCharsets.UTF_8),
                "https://example.test/LIO_Open01/MapServer/25/query",
                Map.of(),
                200,
                "application/json"
        )));

        Lake resolved = resolver.resolve(lake);

        assertThat(resolved.getOgfId()).isEqualTo(OntarioFixtures.RICE_OGF);
        assertThat(resolved.getOfficialName()).isEqualTo("Rice Lake");
        assertThat(resolved.getBboxMinLng()).isNotNull();
    }

    @Test
    void failsWhenMatchesAreNotUnique() {
        Lake lake = lake("Mystery Lake", 45.0, -79.0);
        String twins = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"OGF_ID":1,"OFFICIAL_NAME":"Mystery Lake","WATERBODY_TYPE":"Lake"},
                   "geometry":{"type":"Polygon","coordinates":[[[-79.02,44.98],[-78.98,44.98],[-78.98,45.02],[-79.02,45.02],[-79.02,44.98]]]}},
                  {"type":"Feature","properties":{"OGF_ID":2,"OFFICIAL_NAME":"Mystery Lake","WATERBODY_TYPE":"Lake"},
                   "geometry":{"type":"Polygon","coordinates":[[[-79.02,44.98],[-78.98,44.98],[-78.98,45.02],[-79.02,45.02],[-79.02,44.98]]]}}
                ]}
                """;
        when(featureClient.query(any())).thenReturn(List.of(new RawPage(
                1, twins.getBytes(StandardCharsets.UTF_8), "url", Map.of(), 200, "application/json"
        )));

        assertThatThrownBy(() -> resolver.resolve(lake))
                .isInstanceOf(IdentityResolutionException.class);
    }

    @Test
    void resolvesUsingLioOfficialNameLabel() {
        Lake lake = lake("Head Lake", 44.75, -78.92);
        String body = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"OGF_ID":551154028,"OFFICIAL_NAME_LABEL":"Head Lake","WATERBODY_TYPE":"Lake","GEL_NAME_IDENT":"abc"},
                   "geometry":{"type":"Polygon","coordinates":[[[-78.93,44.74],[-78.91,44.74],[-78.91,44.76],[-78.93,44.76],[-78.93,44.74]]]}}
                ]}
                """;
        when(featureClient.query(any())).thenReturn(List.of(new RawPage(
                1, body.getBytes(StandardCharsets.UTF_8), "url", Map.of(), 200, "application/json"
        )));

        Lake resolved = resolver.resolve(lake);

        assertThat(resolved.getOgfId()).isEqualTo(551154028L);
        assertThat(resolved.getOfficialName()).isEqualTo("Head Lake");
        assertThat(resolved.getWaterbodyLid()).isEqualTo("abc");
    }

    @Test
    void fourLakesUseTheSameResolver() {
        for (OntarioFixtures.LakeSpec spec : List.of(
                OntarioFixtures.HEAD, OntarioFixtures.RICE, OntarioFixtures.SCUGOG, OntarioFixtures.SIMCOE
        )) {
            Lake lake = lake(spec.name(), spec.lat(), spec.lng());
            when(featureClient.query(any())).thenReturn(List.of(OntarioFixtures.page(
                    1,
                    OntarioFixtures.waterbody(spec),
                    new FeatureQuery("https://example.test/LIO_Open01/MapServer/25", "1=1", spec.lng() - 0.05, spec.lat() - 0.05, spec.lng() + 0.05, spec.lat() + 0.05, 2000)
            )));

            Lake resolved = resolver.resolve(lake);
            assertThat(resolved.getOgfId()).isEqualTo(spec.ogfId());
            assertThat(resolved.getOfficialName()).isEqualTo(spec.name());
        }
    }

    private Lake lake(String name, double lat, double lng) {
        Lake lake = new Lake();
        lake.setName(name);
        lake.setCentroid(geoMapper.toPoint(new GeoPointDto(lat, lng)));
        return lake;
    }
}
