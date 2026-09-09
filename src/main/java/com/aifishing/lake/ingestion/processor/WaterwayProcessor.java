package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import org.locationtech.jts.geom.Geometry;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WaterwayProcessor implements DatasetProcessor {

    private final DatasetType datasetType;
    private final String waterwayType;
    private final ProvenanceBinder provenanceBinder;
    private final CrsTransformer crsTransformer;
    private final LakeWaterwayRepository repository;

    public WaterwayProcessor(
            DatasetType datasetType,
            String waterwayType,
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            LakeWaterwayRepository repository
    ) {
        this.datasetType = datasetType;
        this.waterwayType = waterwayType;
        this.provenanceBinder = provenanceBinder;
        this.crsTransformer = crsTransformer;
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return datasetType;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        Map<String, LakeWaterway> records = new LinkedHashMap<>();
        int anonymous = 0;
        for (ParsedFeature feature : features) {
            Geometry geometry = crsTransformer.toWgs84(feature.geometry(), feature.geometry() == null ? null : feature.geometry().getSRID());
            GeometrySupport.requireValid(geometry, waterwayType);
            LakeWaterway record = new LakeWaterway();
            provenanceBinder.bind(record, lake, provider, importVersion, feature);
            record.setType(waterwayType);
            record.setName(FeatureProperties.text(
                    feature.properties(),
                    "OFFICIAL_NAME_LABEL",
                    "OFFICIAL_NAME",
                    "GEOGRAPHIC_NAME",
                    "NAME",
                    "WATERCOURSE_NAME"
            ));
            record.setGeometry(geometry);
            String key = feature.sourceRecordId() != null ? feature.sourceRecordId() : ("anon-" + anonymous++);
            records.putIfAbsent(key, record);
        }
        return NormalizeResult.available(new ArrayList<>(records.values()));
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProviderAndType(lakeId, provider, waterwayType);
        repository.saveAll(records.stream().map(LakeWaterway.class::cast).toList());
    }
}
