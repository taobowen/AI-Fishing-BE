package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.FishStockingRecord;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.mapping.FishSpeciesMapper;
import com.aifishing.lake.ingestion.repo.FishStockingRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FishStockingProcessor implements DatasetProcessor {

    private final ProvenanceBinder provenanceBinder;
    private final FishSpeciesMapper speciesMapper;
    private final FishStockingRecordRepository repository;

    public FishStockingProcessor(
            ProvenanceBinder provenanceBinder,
            FishSpeciesMapper speciesMapper,
            FishStockingRecordRepository repository
    ) {
        this.provenanceBinder = provenanceBinder;
        this.speciesMapper = speciesMapper;
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return DatasetType.FISH_STOCKING;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        List<FishStockingRecord> records = new ArrayList<>();
        for (ParsedFeature feature : features) {
            FishStockingRecord record = new FishStockingRecord();
            provenanceBinder.bind(record, lake, provider, importVersion, feature);
            String speciesName = FeatureProperties.text(feature.properties(), "SPECIES", "SPECIES_NAME", "FISH_SPECIES");
            record.setSourceSpeciesName(speciesName);
            record.setSpecies(speciesMapper.map(speciesName).orElse(null));
            record.setStockingYear(FeatureProperties.integer(feature.properties(), "YEAR", "STOCKING_YEAR"));
            record.setStockingDate(FeatureProperties.date(feature.properties(), "STOCKING_DATE", "DATE"));
            record.setQuantity(FeatureProperties.integer(feature.properties(), "QUANTITY", "NUMBER", "NUMBER_STOCKED", "NO_STOCKED"));
            record.setLifeStage(FeatureProperties.text(feature.properties(), "LIFE_STAGE", "STAGE"));
            records.add(record);
        }
        return NormalizeResult.available(records);
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProvider(lakeId, provider);
        repository.saveAll(records.stream().map(FishStockingRecord.class::cast).toList());
    }
}
