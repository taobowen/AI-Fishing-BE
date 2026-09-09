package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.BathymetryPoint;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.repo.BathymetryPointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class BathymetryPointProcessor implements DatasetProcessor {

    private final ProvenanceBinder provenanceBinder;
    private final CrsTransformer crsTransformer;
    private final BathymetryPointRepository repository;

    public BathymetryPointProcessor(
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            BathymetryPointRepository repository
    ) {
        this.provenanceBinder = provenanceBinder;
        this.crsTransformer = crsTransformer;
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return DatasetType.BATHYMETRY_POINT;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        List<BathymetryPoint> records = new ArrayList<>();
        for (ParsedFeature feature : features) {
            BathymetryPoint record = new BathymetryPoint();
            provenanceBinder.bind(record, lake, provider, importVersion, feature);
            record.setLocation(GeometrySupport.asPoint(
                    crsTransformer.toWgs84(feature.geometry(), feature.geometry().getSRID()),
                    "bathymetry point"
            ));
            String unit = FeatureProperties.text(feature.properties(), "UNIT", "UNITS", "DEPTH_UNIT");
            BigDecimal depth = FeatureProperties.decimal(
                    feature.properties(),
                    "DEPTH_M",
                    "DEPTH",
                    "Z",
                    "VALUE"
            );
            if (depth == null && FeatureProperties.decimal(feature.properties(), "DEPTH_FT") != null) {
                depth = FeatureProperties.decimal(feature.properties(), "DEPTH_FT");
                unit = "ft";
            }
            record.setDepthM(DepthUnitConverter.toMeters(depth, unit));
            record.setSurveyDate(FeatureProperties.date(feature.properties(), "SURVEY_DATE", "YEAR"));
            record.setSurveyMethod(FeatureProperties.text(feature.properties(), "SURVEY_METHOD", "METHOD"));
            record.setAccuracy(FeatureProperties.text(feature.properties(), "ACCURACY", "HORIZONTAL_ACCURACY"));
            records.add(record);
        }
        return NormalizeResult.available(records);
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProvider(lakeId, provider);
        repository.saveAll(records.stream().map(BathymetryPoint.class::cast).toList());
    }
}
