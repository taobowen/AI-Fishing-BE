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

    public record CatalogLake(UUID id, String name, String slug, double lat, double lng) {
        public String cardImagePath() {
            return "lakes/" + slug + ".jpg";
        }
    }

    public static final List<CatalogLake> VALIDATION_LAKES = List.of(
            new CatalogLake(DevSeedIds.LAKE_ID, "Head Lake", "head", 44.75, -78.92),
            new CatalogLake(DevSeedIds.RICE_LAKE_ID, "Rice Lake", "rice", 44.18, -78.17),
            new CatalogLake(DevSeedIds.SCUGOG_LAKE_ID, "Lake Scugog", "scugog", 44.15, -78.90),
            new CatalogLake(DevSeedIds.SIMCOE_LAKE_ID, "Lake Simcoe", "simcoe", 44.42, -79.37),
            new CatalogLake(DevSeedIds.BALSAM_LAKE_ID, "Balsam Lake", "balsam", 44.58, -78.85),
            new CatalogLake(DevSeedIds.PIGEON_LAKE_ID, "Pigeon Lake", "pigeon", 44.50, -78.50),
            new CatalogLake(DevSeedIds.STURGEON_LAKE_ID, "Sturgeon Lake", "sturgeon", 44.47, -78.73),
            new CatalogLake(DevSeedIds.COUCHICHING_LAKE_ID, "Lake Couchiching", "couchiching", 44.67, -79.38),
            new CatalogLake(DevSeedIds.CANAL_LAKE_ID, "Canal Lake", "canal", 44.56, -79.05),
            new CatalogLake(DevSeedIds.SPARROW_LAKE_ID, "Sparrow Lake", "sparrow", 44.82, -79.40),
            new CatalogLake(DevSeedIds.WILCOX_LAKE_ID, "Lake Wilcox", "wilcox", 43.95, -79.44),
            new CatalogLake(DevSeedIds.MUSSELMANS_LAKE_ID, "Musselman's Lake", "musselmans", 44.02, -79.27),
            new CatalogLake(DevSeedIds.PRESTON_LAKE_ID, "Preston Lake", "preston", 44.03, -79.37),
            new CatalogLake(DevSeedIds.HEART_LAKE_ID, "Heart Lake", "heart", 43.74, -79.79),
            new CatalogLake(DevSeedIds.PROFESSORS_LAKE_ID, "Professor's Lake", "professors", 43.75, -79.75),
            new CatalogLake(DevSeedIds.ISLAND_RESERVOIR_ID, "Island Lake Reservoir", "island-reservoir", 43.93, -80.07),
            new CatalogLake(DevSeedIds.BELWOOD_LAKE_ID, "Belwood Lake", "belwood", 43.78, -80.33),
            new CatalogLake(DevSeedIds.MOUNTSBERG_RESERVOIR_ID, "Mountsberg Reservoir", "mountsberg", 43.45, -80.03),
            new CatalogLake(DevSeedIds.CHRISTIE_LAKE_ID, "Christie Lake", "christie", 43.28, -80.02),
            new CatalogLake(DevSeedIds.BUCKHORN_LAKE_ID, "Buckhorn Lake", "buckhorn", 44.48, -78.38),
            new CatalogLake(DevSeedIds.LOVESICK_LAKE_ID, "Lovesick Lake", "lovesick", 44.56, -78.24)
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
                    .map(existing -> new EnsureResult(backfillCardImagePath(existing, spec), false))
                    .orElseGet(() -> new EnsureResult(insertStub(spec), true)));
        }
        return lakes;
    }

    public record EnsureResult(Lake lake, boolean created) {
    }

    private Lake backfillCardImagePath(Lake existing, CatalogLake spec) {
        if (existing.getCardImagePath() == null || existing.getCardImagePath().isBlank()) {
            existing.setCardImagePath(spec.cardImagePath());
            return lakeRepository.save(existing);
        }
        return existing;
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
        lake.setCardImagePath(spec.cardImagePath());
        return lakeRepository.save(lake);
    }
}
