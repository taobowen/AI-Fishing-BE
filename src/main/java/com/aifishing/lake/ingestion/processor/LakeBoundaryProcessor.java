package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.LakeBoundaryRecord;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class LakeBoundaryProcessor implements DatasetProcessor {

    private final ProvenanceBinder provenanceBinder;
    private final CrsTransformer crsTransformer;
    private final LakeBoundaryRecordRepository repository;

    public LakeBoundaryProcessor(
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            LakeBoundaryRecordRepository repository
    ) {
        this.provenanceBinder = provenanceBinder;
        this.crsTransformer = crsTransformer;
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return DatasetType.LAKE_BOUNDARY;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        List<LakeBoundaryRecord> records = new ArrayList<>();
        for (ParsedFeature feature : features) {
            if (lake.getOgfId() != null) {
                Long ogfId = FeatureProperties.longValue(feature.properties(), "OGF_ID");
                if (ogfId != null && !ogfId.equals(lake.getOgfId())) {
                    continue;
                }
            }
            LakeBoundaryRecord record = new LakeBoundaryRecord();
            provenanceBinder.bind(record, lake, provider, importVersion, feature);
            record.setGeometry(GeometrySupport.asMultiPolygon(
                    crsTransformer.toWgs84(feature.geometry(), feature.geometry().getSRID()),
                    "lake boundary"
            ));
            records.add(record);
        }
        return NormalizeResult.available(records);
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProvider(lakeId, provider);
        repository.saveAll(records.stream().map(LakeBoundaryRecord.class::cast).toList());
    }
}
