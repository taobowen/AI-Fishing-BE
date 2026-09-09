package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.Wetland;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.repo.WetlandRepository;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class WetlandProcessor implements DatasetProcessor {

    private final ProvenanceBinder provenanceBinder;
    private final CrsTransformer crsTransformer;
    private final WetlandRepository repository;

    public WetlandProcessor(
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            WetlandRepository repository
    ) {
        this.provenanceBinder = provenanceBinder;
        this.crsTransformer = crsTransformer;
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return DatasetType.WETLAND;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        List<Wetland> records = new ArrayList<>();
        for (ParsedFeature feature : features) {
            Geometry geometry = crsTransformer.toWgs84(feature.geometry(), feature.geometry().getSRID());
            GeometrySupport.requireValid(geometry, "wetland");
            Wetland record = new Wetland();
            provenanceBinder.bind(record, lake, provider, importVersion, feature);
            record.setWetlandType(FeatureProperties.text(
                    feature.properties(),
                    "WETLAND_TYPE",
                    "WETLAND_CLASS",
                    "SIGNIFICANCE",
                    "TYPE"
            ));
            record.setGeometry(geometry);
            records.add(record);
        }
        return NormalizeResult.available(records);
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProvider(lakeId, provider);
        repository.saveAll(records.stream().map(Wetland.class::cast).toList());
    }
}
