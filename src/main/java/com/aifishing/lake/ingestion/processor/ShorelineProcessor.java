package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import org.springframework.stereotype.Component;

@Component
public class ShorelineProcessor extends WaterwayProcessor {

    public ShorelineProcessor(
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            LakeWaterwayRepository repository
    ) {
        super(DatasetType.SHORELINE, "SHORELINE", provenanceBinder, crsTransformer, repository);
    }
}
