package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import org.springframework.stereotype.Component;

@Component
public class IslandProcessor extends WaterwayProcessor {

    public IslandProcessor(
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            LakeWaterwayRepository repository
    ) {
        super(DatasetType.ISLAND, "ISLAND", provenanceBinder, crsTransformer, repository);
    }
}
