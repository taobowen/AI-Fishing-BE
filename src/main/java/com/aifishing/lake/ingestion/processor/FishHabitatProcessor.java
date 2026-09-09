package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.FishHabitat;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.mapping.FishSpeciesMapper;
import com.aifishing.lake.ingestion.repo.FishHabitatRepository;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FishHabitatProcessor implements DatasetProcessor {

    private final ProvenanceBinder provenanceBinder;
    private final CrsTransformer crsTransformer;
    private final FishSpeciesMapper speciesMapper;
    private final FishHabitatRepository repository;

    public FishHabitatProcessor(
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            FishSpeciesMapper speciesMapper,
            FishHabitatRepository repository
    ) {
        this.provenanceBinder = provenanceBinder;
        this.crsTransformer = crsTransformer;
        this.speciesMapper = speciesMapper;
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return DatasetType.FISH_HABITAT;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        List<FishHabitat> records = new ArrayList<>();
        for (ParsedFeature feature : features) {
            FishHabitat record = new FishHabitat();
            provenanceBinder.bind(record, lake, provider, importVersion, feature);
            String speciesName = FeatureProperties.text(feature.properties(), "SPECIES", "SPECIES_NAME", "FISH_SPECIES");
            record.setSourceSpeciesName(speciesName);
            record.setSpecies(speciesMapper.map(speciesName).orElse(null));
            String habitatType = FeatureProperties.text(
                    feature.properties(),
                    "ACTIVITY_TYPE",
                    "FISH_ACTIVITY_TYPE",
                    "HABITAT_TYPE",
                    "TYPE"
            );
            record.setHabitatType(habitatType == null ? "FISH_ACTIVITY" : habitatType);
            if (feature.geometry() != null) {
                Geometry geometry = crsTransformer.toWgs84(feature.geometry(), feature.geometry().getSRID());
                GeometrySupport.requireValid(geometry, "fish habitat");
                record.setGeometry(geometry);
            }
            records.add(record);
        }
        return NormalizeResult.available(records);
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProvider(lakeId, provider);
        repository.saveAll(records.stream().map(FishHabitat.class::cast).toList());
    }
}
