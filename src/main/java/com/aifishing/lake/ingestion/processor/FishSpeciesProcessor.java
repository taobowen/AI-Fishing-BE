package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.LakeFishSpecies;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.mapping.FishSpeciesMapper;
import com.aifishing.lake.ingestion.repo.LakeFishSpeciesRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class FishSpeciesProcessor implements DatasetProcessor {

    private final ProvenanceBinder provenanceBinder;
    private final FishSpeciesMapper speciesMapper;
    private final LakeFishSpeciesRepository repository;

    public FishSpeciesProcessor(
            ProvenanceBinder provenanceBinder,
            FishSpeciesMapper speciesMapper,
            LakeFishSpeciesRepository repository
    ) {
        this.provenanceBinder = provenanceBinder;
        this.speciesMapper = speciesMapper;
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return DatasetType.FISH_SPECIES;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        Map<String, LakeFishSpecies> records = new LinkedHashMap<>();
        for (ParsedFeature feature : features) {
            String summary = FeatureProperties.text(
                    feature.properties(),
                    "FISH_SPECIES_SUMMARY",
                    "SPECIES_SUMMARY",
                    "SPECIES"
            );
            if (summary == null || summary.isBlank()) {
                continue;
            }
            String waterbodyLid = FeatureProperties.text(feature.properties(), "WATERBODY_LID", "WB_LID");
            if (waterbodyLid != null && lake.getWaterbodyLid() == null) {
                lake.setWaterbodyLid(waterbodyLid);
            }
            if (lake.getOfficialName() == null) {
                lake.setOfficialName(FeatureProperties.text(
                        feature.properties(),
                        "OFFICIAL_WATERBODY_NAME",
                        "OFFICIAL_NAME_LABEL",
                        "OFFICIAL_NAME"
                ));
            }
            for (String speciesName : summary.split("[,;/|]")) {
                String trimmed = speciesName.trim();
                if (trimmed.isBlank()) {
                    continue;
                }
                LakeFishSpecies record = new LakeFishSpecies();
                provenanceBinder.bind(record, lake, provider, importVersion, feature);
                if (feature.sourceRecordId() != null) {
                    record.setSourceRecordId(feature.sourceRecordId() + ":" + trimmed);
                }
                record.setSourceSpeciesName(trimmed);
                record.setSpecies(speciesMapper.map(trimmed).orElse(null));
                record.setObservationType("ARA_SUMMARY");
                String key = record.getSourceRecordId() != null
                        ? record.getSourceRecordId()
                        : trimmed;
                records.putIfAbsent(key, record);
            }
        }
        return NormalizeResult.available(new ArrayList<>(records.values()));
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProvider(lakeId, provider);
        repository.saveAll(records.stream().map(LakeFishSpecies.class::cast).toList());
    }
}
