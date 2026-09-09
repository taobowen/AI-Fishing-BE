package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import org.springframework.stereotype.Component;

@Component
public class WatercourseProcessor extends WaterwayProcessor {

    public WatercourseProcessor(
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            LakeWaterwayRepository repository
    ) {
        super(DatasetType.WATERWAY, "WATERCOURSE", provenanceBinder, crsTransformer, repository);
    }
}
