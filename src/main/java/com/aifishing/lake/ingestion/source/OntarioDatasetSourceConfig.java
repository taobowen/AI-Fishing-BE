package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.ingestion.OntarioProperties;
import com.aifishing.lake.ingestion.dto.DatasetType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OntarioDatasetSourceConfig {

    @Bean
    OntarioDatasetSource lakeBoundarySource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen01Base(), lio.getWaterbodyLayer());
        return new IntersectingArcGisDatasetSource(DatasetType.LAKE_BOUNDARY, url, client, lake -> {
            if (lake.getOgfId() != null) {
                return LakeQuerySupport.byOgfId(url, lake.getOgfId(), properties.getPageSize());
            }
            return LakeQuerySupport.intersecting(url, lake, properties.getPageSize());
        });
    }

    @Bean
    OntarioDatasetSource shorelineSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen01Base(), lio.getShorelineLayer());
        return intersecting(DatasetType.SHORELINE, url, client, properties);
    }

    @Bean
    OntarioDatasetSource islandSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen01Base(), lio.getHydroPolyLayer());
        return intersecting(DatasetType.ISLAND, url, client, properties);
    }

    @Bean
    OntarioDatasetSource waterwaySource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen01Base(), lio.getWatercourseLayer());
        return intersecting(DatasetType.WATERWAY, url, client, properties);
    }

    @Bean
    OntarioDatasetSource wetlandSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen01Base(), lio.getWetlandLayer());
        return intersecting(DatasetType.WETLAND, url, client, properties);
    }

    @Bean
    OntarioDatasetSource bathymetryIndexSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen01Base(), lio.getBathymetryIndexLayer());
        return intersecting(DatasetType.BATHYMETRY_INDEX, url, client, properties);
    }

    @Bean
    OntarioDatasetSource bathymetryLineSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen01Base(), lio.getBathymetryLineLayer());
        return intersecting(DatasetType.BATHYMETRY_LINE, url, client, properties);
    }

    @Bean
    OntarioDatasetSource bathymetryPointSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen01Base(), lio.getBathymetryPointLayer());
        return intersecting(DatasetType.BATHYMETRY_POINT, url, client, properties);
    }

    @Bean
    OntarioDatasetSource fishSpeciesSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen07Base(), lio.getAraPolyLayer());
        return intersecting(DatasetType.FISH_SPECIES, url, client, properties);
    }

    @Bean
    OntarioDatasetSource fishStockingSource(OntarioProperties properties, OntarioFeatureClient client) {
        return intersecting(DatasetType.FISH_STOCKING, properties.getLio().getStockingUrl(), client, properties);
    }

    @Bean
    OntarioDatasetSource fishHabitatSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen07Base(), lio.getFishActivityLayer());
        return intersecting(DatasetType.FISH_HABITAT, url, client, properties);
    }

    @Bean
    OntarioDatasetSource accessPointSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen07Base(), lio.getAccessLayer());
        return intersecting(DatasetType.ACCESS_POINT, url, client, properties);
    }

    @Bean
    OntarioDatasetSource fmzSource(OntarioProperties properties, OntarioFeatureClient client) {
        OntarioProperties.Lio lio = properties.getLio();
        String url = lio.layerUrl(lio.getOpen07Base(), lio.getFmzLayer());
        return intersecting(DatasetType.FMZ, url, client, properties);
    }

    @Bean
    OntarioDatasetSource vegetationSource() {
        return new AlwaysUnavailableDatasetSource(
                DatasetType.VEGETATION,
                "No official Ontario vegetation raster/product wired for lake ingest"
        );
    }

    @Bean
    OntarioDatasetSource bottomSubstrateSource() {
        return new AlwaysUnavailableDatasetSource(
                DatasetType.BOTTOM_SUBSTRATE,
                "No official Ontario bottom-substrate product wired for lake ingest"
        );
    }

    private IntersectingArcGisDatasetSource intersecting(
            DatasetType type,
            String url,
            OntarioFeatureClient client,
            OntarioProperties properties
    ) {
        return new IntersectingArcGisDatasetSource(
                type,
                url,
                client,
                lake -> LakeQuerySupport.intersecting(url, lake, properties.getPageSize())
        );
    }
}
