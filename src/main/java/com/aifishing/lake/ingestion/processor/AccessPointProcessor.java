package com.aifishing.lake.ingestion.processor;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.AccessOwnership;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.geo.AccessLakeAssociator;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class AccessPointProcessor implements DatasetProcessor {

    private static final int MAX_REJECTED_IDS = 25;

    private final ProvenanceBinder provenanceBinder;
    private final CrsTransformer crsTransformer;
    private final AccessLakeAssociator associator;
    private final LakeAccessPointRepository repository;

    public AccessPointProcessor(
            ProvenanceBinder provenanceBinder,
            CrsTransformer crsTransformer,
            AccessLakeAssociator associator,
            LakeAccessPointRepository repository
    ) {
        this.provenanceBinder = provenanceBinder;
        this.crsTransformer = crsTransformer;
        this.associator = associator;
        this.repository = repository;
    }

    @Override
    public DatasetType type() {
        return DatasetType.ACCESS_POINT;
    }

    @Override
    public NormalizeResult normalize(
            Lake lake,
            String provider,
            String importVersion,
            List<ParsedFeature> features,
            List<RawPage> pages
    ) {
        List<LakeAccessPoint> associated = new ArrayList<>();
        List<String> rejectedOgfIds = new ArrayList<>();
        int rejectedByDistance = 0;
        for (ParsedFeature feature : features) {
            LakeAccessPoint record = mapFeature(lake, provider, importVersion, feature);
            AccessLakeAssociator.Association association = associator.evaluate(lake, record.getLocation());
            if (!association.associated()) {
                rejectedByDistance++;
                if (rejectedOgfIds.size() < MAX_REJECTED_IDS && record.getSourceRecordId() != null) {
                    rejectedOgfIds.add(record.getSourceRecordId());
                }
                continue;
            }
            record.setAssociationDistanceMeters(association.distanceMeters());
            associated.add(record);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rawFeatureCount", features.size());
        metadata.put("associatedCount", associated.size());
        metadata.put("rejectedByDistanceCount", rejectedByDistance);
        if (!rejectedOgfIds.isEmpty()) {
            metadata.put("rejectedOgfIds", List.copyOf(rejectedOgfIds));
        }
        return NormalizeResult.available(associated, metadata);
    }

    private LakeAccessPoint mapFeature(Lake lake, String provider, String importVersion, ParsedFeature feature) {
        LakeAccessPoint record = new LakeAccessPoint();
        provenanceBinder.bind(record, lake, provider, importVersion, feature);
        applyType(record, FeatureProperties.text(
                feature.properties(),
                "FISHING_ACCESS_POINT_TYPE",
                "ACCESS_TYPE",
                "FACILITY_TYPE",
                "TYPE"
        ));
        record.setName(FeatureProperties.text(
                feature.properties(),
                "SITE_NAME",
                "NAME",
                "ACCESS_POINT_NAME",
                "OFFICIAL_NAME"
        ));
        record.setLocation(GeometrySupport.asPoint(
                crsTransformer.toWgs84(feature.geometry(), feature.geometry().getSRID()),
                "access point"
        ));
        record.setRoadAccess(FeatureProperties.bool(feature.properties(), "ROAD_ACCESS", "ROAD"));
        record.setParking(FeatureProperties.bool(feature.properties(), "PARKING_PRESENCE_FLG", "PARKING"));
        record.setOwnershipType(AccessOwnership.normalize(FeatureProperties.text(
                feature.properties(),
                "SITE_OWNERSHIP_TYPE",
                "OWNERSHIP_TYPE",
                "OWNERSHIP"
        )));
        return record;
    }

    static void applyType(LakeAccessPoint record, String rawType) {
        if (rawType != null && isBoatLaunchType(rawType)) {
            record.setType("BOAT_LAUNCH");
            record.setBoatLaunch(true);
            return;
        }
        if (rawType != null && isShoreAccessType(rawType)) {
            record.setType("SHORELINE_ACCESS");
            record.setShoreAccess(true);
            return;
        }
        record.setType("FISHING_ACCESS");
    }

    private static boolean isBoatLaunchType(String rawType) {
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        return "boat launch".equals(normalized) || "boat_launch".equals(normalized);
    }

    private static boolean isShoreAccessType(String rawType) {
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        return "shoreline access".equals(normalized)
                || "shoreline_access".equals(normalized)
                || "shore".equals(normalized);
    }

    @Override
    @Transactional
    public void replaceCanonical(UUID lakeId, String provider, List<?> records) {
        repository.deleteByLakeIdAndProvider(lakeId, provider);
        repository.saveAll(records.stream().map(LakeAccessPoint.class::cast).toList());
    }
}
