package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.BathymetryContour;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.repo.BathymetryContourRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class BathymetryLineProcessor implements DatasetProcessor {

    private final ProvenanceBinder provenanceBinder;
    private final CrsTransformer crsTransformer;
    private final BathymetryContourRepository repository;

    public BathymetryLineProcessor(
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            BathymetryContourRepository repository
    ) {
        this.provenanceBinder = provenanceBinder;
        this.crsTransformer = crsTransformer;
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return DatasetType.BATHYMETRY_LINE;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        List<BathymetryContour> records = new ArrayList<>();
        for (ParsedFeature feature : features) {
            BathymetryContour record = new BathymetryContour();
            provenanceBinder.bind(record, lake, provider, importVersion, feature);
            record.setGeometry(GeometrySupport.asMultiLineString(
                    crsTransformer.toWgs84(feature.geometry(), feature.geometry().getSRID()),
                    "bathymetry contour"
            ));
            String unit = FeatureProperties.text(feature.properties(), "UNIT", "UNITS", "DEPTH_UNIT");
            BigDecimal depth = FeatureProperties.decimal(
                    feature.properties(),
                    "DEPTH_M",
                    "DEPTH",
                    "CONTOUR",
                    "Z",
                    "VALUE",
                    "ELEVATION"
            );
            if (depth == null && FeatureProperties.decimal(feature.properties(), "DEPTH_FT") != null) {
                depth = FeatureProperties.decimal(feature.properties(), "DEPTH_FT");
                unit = "ft";
            }
            record.setDepthM(DepthUnitConverter.toMeters(depth, unit));
            record.setSurveyDate(FeatureProperties.date(feature.properties(), "SURVEY_DATE", "YEAR"));
            record.setSurveyMethod(FeatureProperties.text(feature.properties(), "SURVEY_METHOD", "METHOD"));
            record.setHorizontalAccuracy(FeatureProperties.text(feature.properties(), "HORIZONTAL_ACCURACY", "ACCURACY"));
            records.add(record);
        }
        return NormalizeResult.available(records);
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProvider(lakeId, provider);
        repository.saveAll(records.stream().map(BathymetryContour.class::cast).toList());
    }
}
