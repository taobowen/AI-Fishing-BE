package com.aifishing.seed;

import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ValidationCatalogService {

    public record CatalogLake(UUID id, String name, double lat, double lng) {
    }

    public static final List<CatalogLake> VALIDATION_LAKES = List.of(
            new CatalogLake(DevSeedIds.LAKE_ID, "Head Lake", 44.75, -78.92),
            new CatalogLake(DevSeedIds.RICE_LAKE_ID, "Rice Lake", 44.18, -78.17),
            new CatalogLake(DevSeedIds.SCUGOG_LAKE_ID, "Lake Scugog", 44.15, -78.90),
            new CatalogLake(DevSeedIds.SIMCOE_LAKE_ID, "Lake Simcoe", 44.42, -79.37)
    );

    private final LakeRepository lakeRepository;
    private final GeoMapper geoMapper;

    public ValidationCatalogService(LakeRepository lakeRepository, GeoMapper geoMapper) {
        this.lakeRepository = lakeRepository;
        this.geoMapper = geoMapper;
    }

    @Transactional
    public List<EnsureResult> ensureCatalog() {
        List<EnsureResult> lakes = new ArrayList<>();
        for (CatalogLake spec : VALIDATION_LAKES) {
            lakes.add(lakeRepository.findById(spec.id())
                    .map(existing -> new EnsureResult(existing, false))
                    .orElseGet(() -> new EnsureResult(insertStub(spec), true)));
        }
        return lakes;
    }

    public record EnsureResult(Lake lake, boolean created) {
    }

    private Lake insertStub(CatalogLake spec) {
        Lake lake = new Lake();
        lake.setId(spec.id());
        lake.setName(spec.name());
        lake.setProvince("Ontario");
        lake.setCountry("Canada");
        lake.setSource("MANUAL_SEED");
        lake.setCentroid(geoMapper.toPoint(new GeoPointDto(spec.lat(), spec.lng())));
        lake.setTimeZoneId("America/Toronto");
        return lakeRepository.save(lake);
    }
}
