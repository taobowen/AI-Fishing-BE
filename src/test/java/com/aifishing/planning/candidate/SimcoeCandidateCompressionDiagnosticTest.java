package com.aifishing.planning.candidate;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Frozen Simcoe-shaped A–E diagnostic. One semantic change per run.
 * Live Generate Plan A (trip 1f4a9b46, plan f27145d6): 17058 targets, 1670 zones,
 * 2866 beforeDedup, 32 after, DUPLICATE 2834, 0 physicalZonesConsidered.
 */
class SimcoeCandidateCompressionDiagnosticTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double LAUNCH_A_LAT = 44.320;
    private static final double LAUNCH_A_LNG = -79.530;
    private static final double LAUNCH_B_LAT = 44.409;
    private static final double LAUNCH_B_LNG = -79.580;
    private static final double NEAR_KM = 2.0;

    private final CandidateDeduper deduper = new CandidateDeduper();

    @Test
    void frozenSimcoeReplaySeparatesQuotaFromSpatialDedupe() throws Exception {
        Field field = SimcoeShapedField.build();
        CandidateDeduper.Result a = run(field.candidates, 150, 10, 32, CandidateDeduper.Mode.CURRENT);
        CandidateDeduper.Result b = run(field.candidates, 150, 10, 64, CandidateDeduper.Mode.CURRENT);
        CandidateDeduper.Result c = run(field.candidates, 150, 10, 128, CandidateDeduper.Mode.CURRENT);
        CandidateDeduper.Result d = run(field.candidates, 150, 0, 0, CandidateDeduper.Mode.CURRENT);
        CandidateDeduper.Result e = run(field.candidates, 0, 0, 0, CandidateDeduper.Mode.TRUE_DUPLICATE_ONLY);

        assertThat(a.kept()).hasSize(32);
        assertThat(b.kept().size()).isBetween(32, 64);
        assertThat(c.kept().size()).isBetween(b.kept().size(), 128);
        assertThat(d.kept().size()).isGreaterThan(a.kept().size());
        assertThat(e.kept().size()).isGreaterThan(d.kept().size());
        assertThat(a.kept()).isEqualTo(deduper.dedupe(field.candidates, 150, 10, 32));

        int quotaGain = d.kept().size() - a.kept().size();
        int spatialGain = e.kept().size() - d.kept().size();
        String dominating;
        if (quotaGain > spatialGain * 1.25) {
            dominating = "global quota";
        } else if (spatialGain > quotaGain * 1.25) {
            dominating = "spatial 150m/overlap";
        } else {
            dominating = "both";
        }

        Path report = Path.of("docs", "reports", "simcoe-candidate-compression.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, markdown(field, a, b, c, d, e, dominating, quotaGain, spatialGain));

        assertThat(dominating).isIn("global quota", "spatial 150m/overlap", "both");
        assertThat(nearLaunch(d.kept(), LAUNCH_A_LAT, LAUNCH_A_LNG))
                .isGreaterThanOrEqualTo(nearLaunch(a.kept(), LAUNCH_A_LAT, LAUNCH_A_LNG));
        assertThat(unassigned(a.kept(), field.zoneByTarget)).isGreaterThanOrEqualTo(0);
        assertThat(nearLaunch(e.kept(), LAUNCH_B_LAT, LAUNCH_B_LNG)).isZero();
        assertThat(field.uniqueIds).isEqualTo(1964);
        assertThat(field.unassignedUnique).isEqualTo(1650);
        assertThat(field.zoneCount).isEqualTo(175);
    }

    private CandidateDeduper.Result run(
            List<CandidateSpot> candidates,
            double minSpacingM,
            int maxPerType,
            int maxTotal,
            CandidateDeduper.Mode mode
    ) {
        return deduper.diagnose(candidates, minSpacingM, maxPerType, maxTotal, mode);
    }

    private static String markdown(
            Field field,
            CandidateDeduper.Result a,
            CandidateDeduper.Result b,
            CandidateDeduper.Result c,
            CandidateDeduper.Result d,
            CandidateDeduper.Result e,
            String dominating,
            int quotaGain,
            int spatialGain
    ) {
        StringBuilder out = new StringBuilder();
        out.append("# Simcoe candidate compression diagnostic\n\n");
        out.append("Frozen replay of current Generate Plan compression semantics. ");
        out.append("One variable per run. No strategy AI. ");
        out.append("Fixture is Simcoe-shaped (two Public Access ramps, dense mixed water near the south ramp, ");
        out.append("high-score basin 3.5 km away, lake-wide stars, two strategy-window copies).\n\n");
        out.append("Live Generate Plan A (trip `1f4a9b46-f94f-4093-b4f2-3a1eb0d4f4d3`, plan `f27145d6`): ");
        out.append("17058 snapshot targets, 1670 zones, 2866 beforeDedup, 32 after, lumped `DUPLICATE` 2834, ");
        out.append("boat filter 5 accepted / 27 `BOAT_TRAVEL_UNREASONABLE`, `physicalZonesConsidered` 0, ");
        out.append("1 waypoint, ~88 min travel, 332 min unused, beam 2422/8000.\n\n");
        out.append("## A–E compression results\n\n");
        out.append("| run | semantics | before | after | unique ids | near launch A (2 km) | near launch B (2 km) | zones represented | unassigned | TIME_VARIANT_MERGED | SPATIAL_NEAR | OVERLAP | TYPE_BUDGET | GLOBAL_QUOTA |\n");
        out.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        out.append(row("A", "current maxTotal=32", field, a));
        out.append(row("B", "current maxTotal=64", field, b));
        out.append(row("C", "current maxTotal=128", field, c));
        out.append(row("D", "no quota; keep 150m/overlap", field, d));
        out.append(row("E", "true-duplicate-only; quotas and 150m off", field, e));
        out.append('\n');
        out.append("## Mechanism isolation\n\n");
        out.append("- A vs D (quota removed, 150m kept): kept ").append(a.kept().size())
                .append(" → ").append(d.kept().size())
                .append(" (quota-bound surplus **").append(quotaGain).append("**).\n");
        out.append("- D vs E (150m/overlap removed, quotas still off): kept ").append(d.kept().size())
                .append(" → ").append(e.kept().size())
                .append(" (spatial-dedupe surplus **").append(spatialGain).append("**).\n");
        out.append("- Nearby dense cluster (launch A, 2 km): A=")
                .append(nearLaunch(a.kept(), LAUNCH_A_LAT, LAUNCH_A_LNG))
                .append(", D=")
                .append(nearLaunch(d.kept(), LAUNCH_A_LAT, LAUNCH_A_LNG))
                .append(", E=")
                .append(nearLaunch(e.kept(), LAUNCH_A_LAT, LAUNCH_A_LNG))
                .append(".\n\n");
        out.append("## Dominating failure\n\n");
        out.append("**").append(dominating).append("** dominates this frozen replay.\n\n");
        if ("both".equals(dominating)) {
            out.append("Raising `max-total` alone is not the architecture: D still discards mixed-type neighbors ");
            out.append("inside 150 m, which is what empties PhysicalZones (`cluster-min-members=3` on survivors). ");
            out.append("Removing 150 m without replacing lake-wide top-K would also flood Beam. ");
            out.append("The rewrite must replace lake-wide score top-K **and** stop treating mixed-type/depth ");
            out.append("neighbors as duplicates, then apply a zone-aware bounded macro shortlist.\n");
        } else if ("global quota".equals(dominating)) {
            out.append("150 m/overlap is not the main loss: D already keeps most of E. ");
            out.append("Replace lake-wide top-K with a stratified zone-aware shortlist. ");
            out.append("Do not restore a larger intrinsic-score top-K as the architecture.\n");
        } else {
            out.append("Quota is not the main loss: D is close to A. ");
            out.append("Stop treating mixed-type/depth neighbors inside 150 m as duplicates. ");
            out.append("Keep a bounded macro set so Beam never sees thousands of flat visits.\n");
        }
        out.append("\nFixture: unique physical targets=").append(field.uniqueIds)
                .append(", windowed rows=").append(field.candidates.size())
                .append(", snapshot-like zones=").append(field.zoneCount)
                .append(", unassigned unique targets=").append(field.unassignedUnique)
                .append(".\n");
        out.append(LIVE_MEMBERSHIP_AND_LAUNCH_B);
        return out.toString();
    }

    static final String LIVE_MEMBERSHIP_AND_LAUNCH_B = """

## Live Simcoe PhysicalZone membership audit

Snapshot `777a0073-7a01-4988-978a-ede4946d3cf0` (READY, GIS, 2026-09-15). Read-only query against local PostGIS `127.0.0.1:5433`.

| metric | value |
|---|---:|
| generated targets | 17058 |
| physical zones | 1670 |
| assigned to a zone | 16380 (96.0%) |
| unassigned | 678 (4.0%) |
| skippedTargetCount / skippedByReason | 0 / {} |
| attempted partitions | 1670 |
| skipped partitions | 0 |

Unassigned is **not** skip-ledger `NOT_POLYGONAL`. Builder records no target skips. Leftovers are targets that never joined a component of size >= `cluster-min-members` (3), plus Euclidean-near targets that failed water-path linking.

### Unassigned by target kind

| kind | n | geometry |
|---|---:|---|
| PATH | 634 | ST_LineString |
| POINT | 44 | ST_Point |

Assigned: PATH 15889, POINT 491. PATH is clusterable (buffered corridor). Unassigned PATH is not a contract-level non-polygonal exclusion.

### Unassigned by feature type

| semantic_type | n |
|---|---:|
| DROP_OFF | 397 |
| HUMP | 163 |
| FLAT | 112 |
| POINT | 4 |
| BASIN | 2 |

Kind x type: PATH/DROP_OFF 397, PATH/HUMP 147, PATH/FLAT 89, POINT/FLAT 23, POINT/HUMP 16, POINT/POINT 4, PATH/BASIN 1, POINT/BASIN 1.

### Source / derivation (split_reason)

| split_reason | n |
|---|---:|
| polygon_boundary | 237 |
| orientation_change | 206 |
| max_length | 187 |
| polygon_anchor | 40 |
| whole | 4 |
| point | 4 |

### Spatial density and distance to nearest PhysicalZone

Distance to nearest zone representative (meters): min 65.7, p25 296, p50 435, p75 588, max 1756.

- within 150 m of a zone: 19
- within 550 m (neighbor search radius): 476
- within 1 km: 642
- beyond 1 km: 36

Unassigned–unassigned neighbors within 550 m: 41 isolates, 313 with one neighbor (pairs, below min-members 3), 324 with two or more (Euclidean-dense), mean 1.84 neighbors.

### Classification

- **Legitimate atomics / policy leftovers (primary):** isolates and pairs (~354) under `cluster-min-members=3`; remaining unassigned that are water-path isolated from zones even when Euclidean-near. Snapshot skip ledger is empty, so this is clustering policy, not a recorded skip bug.
- **Watch, not a proven zone-construction bug:** 324 Euclidean-dense unassigned and 476 within 550 m of an existing zone. Clustering uses water-path, not Euclidean, so this is **not** sufficient to treat them as a membership gap. Do **not** hide them in the compressor (no lake-wide duplicate drop). Treat as classified leftover atomics: shortlist buckets + bounded representative visit options. Promote to PhysicalZones only in `FishingZoneBuilder` if a later water-path audit proves they should have clustered.
- **Fixture E 1650 unassigned is synthetic**, not live Simcoe: 600 `star-*` never zoned + ~1050 of 1200 `bg-*` (only every 8th zoned). Live unassigned is 678.

Compressor must not invent a second PhysicalZone type for these 678.

## Launch B (fixture vs live)

Diagnostic D/E **0 within 2 km of launch B** is **fixture geometry**. All `SimcoeShapedField` offsets are from launch A `(44.320, -79.530)`. Launch B `(44.409, -79.580)` is ~10 km NNW; no fixture point is within 2 km.

Live snapshot near launch B: **562 targets and 43 zones within 2 km**. Nearest targets are PATH DROP_OFF at ~68 m. Launch B emptiness is not a compression effect and not a live feature-data gap. Do not pull far-basin or dense-A targets toward B in the compressor.
""";

    private static String row(String name, String semantics, Field field, CandidateDeduper.Result result) {
        return String.format(
                Locale.ROOT,
                "| %s | %s | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d |\n",
                name,
                semantics,
                field.candidates.size(),
                result.kept().size(),
                unique(result.kept()),
                nearLaunch(result.kept(), LAUNCH_A_LAT, LAUNCH_A_LNG),
                nearLaunch(result.kept(), LAUNCH_B_LAT, LAUNCH_B_LNG),
                zonesRepresented(result.kept(), field.zoneByTarget),
                unassigned(result.kept(), field.zoneByTarget),
                result.dropped(CandidateCompressionReason.TIME_VARIANT_MERGED),
                result.dropped(CandidateCompressionReason.SPATIAL_NEAR_REDUNDANCY),
                result.dropped(CandidateCompressionReason.GEOMETRY_OVERLAP_REDUNDANCY),
                result.dropped(CandidateCompressionReason.FEATURE_TYPE_BUDGET),
                result.dropped(CandidateCompressionReason.REGIONAL_CANDIDATE_BUDGET)
        );
    }

    private static int unique(List<CandidateSpot> spots) {
        Set<UUID> ids = new HashSet<>();
        for (CandidateSpot spot : spots) {
            ids.add(spot.getFishingTargetId());
        }
        return ids.size();
    }

    private static int nearLaunch(List<CandidateSpot> spots, double lat, double lng) {
        Point launch = point(lng, lat);
        int count = 0;
        Set<UUID> seen = new HashSet<>();
        for (CandidateSpot spot : spots) {
            if (spot.getLocation() == null || !seen.add(spot.getFishingTargetId())) {
                continue;
            }
            if (GeoMetrics.distanceM(launch, spot.getLocation()) <= NEAR_KM * 1000.0) {
                count++;
            }
        }
        return count;
    }

    private static int zonesRepresented(List<CandidateSpot> spots, Map<UUID, UUID> zoneByTarget) {
        Set<UUID> zones = new HashSet<>();
        for (CandidateSpot spot : spots) {
            UUID zone = zoneByTarget.get(spot.getFishingTargetId());
            if (zone != null) {
                zones.add(zone);
            }
        }
        return zones.size();
    }

    private static int unassigned(List<CandidateSpot> spots, Map<UUID, UUID> zoneByTarget) {
        Set<UUID> ids = new HashSet<>();
        for (CandidateSpot spot : spots) {
            UUID id = spot.getFishingTargetId();
            if (id != null && !zoneByTarget.containsKey(id)) {
                ids.add(id);
            }
        }
        return ids.size();
    }

    private static Point point(double lng, double lat) {
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);
        return point;
    }

    private record Field(
            List<CandidateSpot> candidates,
            Map<UUID, UUID> zoneByTarget,
            int uniqueIds,
            int zoneCount,
            int unassignedUnique
    ) {
    }

    static final class SimcoeShapedField {
        static Field build() {
            List<CandidateSpot> unique = new ArrayList<>();
            Map<UUID, UUID> zoneByTarget = new HashMap<>();
            FeatureType[] mix = {
                    FeatureType.DROP_OFF, FeatureType.HUMP, FeatureType.POINT,
                    FeatureType.FLAT, FeatureType.BASIN, FeatureType.ISLAND_EDGE
            };
            int seq = 0;
            for (int row = 0; row < 12; row++) {
                for (int col = 0; col < 12; col++) {
                    double east = col * 70.0;
                    double north = row * 70.0;
                    FeatureType type = mix[(row + col) % mix.length];
                    double weight = type == FeatureType.BASIN ? 0.72 : 0.80 + ((row + col) % 5) * 0.02;
                    CandidateSpot spot = spot("dense-" + seq, LAUNCH_A_LNG, LAUNCH_A_LAT, east, north, type, weight, 3.5 + (seq % 4) * 0.4);
                    unique.add(spot);
                    UUID zone = UUID.nameUUIDFromBytes(("zone-dense-" + (seq / 6)).getBytes());
                    zoneByTarget.put(spot.getFishingTargetId(), zone);
                    seq++;
                }
            }
            for (int i = 0; i < 20; i++) {
                CandidateSpot spot = spot(
                        "far-basin-" + i,
                        LAUNCH_A_LNG,
                        LAUNCH_A_LAT,
                        2800 + (i % 5) * 200,
                        2200 + (i / 5) * 200,
                        FeatureType.BASIN,
                        0.97,
                        7.0
                );
                unique.add(spot);
                zoneByTarget.put(spot.getFishingTargetId(), UUID.nameUUIDFromBytes("zone-far-basin".getBytes()));
            }
            for (int i = 0; i < 600; i++) {
                double east = 4000 + (i % 40) * 400;
                double north = -2000 + (i / 40) * 500;
                FeatureType type = mix[i % mix.length];
                unique.add(spot("star-" + i, LAUNCH_A_LNG, LAUNCH_A_LAT, east, north, type, 0.88 + (i % 7) * 0.01, 4.0));
            }
            for (int i = 0; i < 1200; i++) {
                double east = -3000 + (i % 50) * 250;
                double north = 500 + (i / 50) * 280;
                FeatureType type = mix[i % mix.length];
                CandidateSpot spot = spot("bg-" + i, LAUNCH_A_LNG, LAUNCH_A_LAT, east, north, type, 0.60 + (i % 9) * 0.02, 4.5);
                unique.add(spot);
                if (i % 8 == 0) {
                    zoneByTarget.put(spot.getFishingTargetId(), UUID.nameUUIDFromBytes(("zone-bg-" + (i / 8)).getBytes()));
                }
            }
            List<CandidateSpot> windowed = new ArrayList<>();
            for (CandidateSpot spot : unique) {
                CandidateSpot morning = spot.copy();
                morning.setWindowFrom(LocalTime.of(7, 0));
                morning.setWindowTo(LocalTime.of(12, 0));
                windowed.add(morning);
                if (Math.abs(spot.getFishingTargetId().getLeastSignificantBits()) % 2 == 0) {
                    CandidateSpot afternoon = spot.copy();
                    afternoon.setWindowFrom(LocalTime.of(12, 0));
                    afternoon.setWindowTo(LocalTime.of(17, 0));
                    afternoon.setStrategyWeight(spot.getStrategyWeight() * 0.98);
                    windowed.add(afternoon);
                }
            }
            Set<UUID> zoneIds = new HashSet<>(zoneByTarget.values());
            int unassigned = 0;
            Set<UUID> seen = new HashSet<>();
            for (CandidateSpot spot : unique) {
                if (seen.add(spot.getFishingTargetId()) && !zoneByTarget.containsKey(spot.getFishingTargetId())) {
                    unassigned++;
                }
            }
            return new Field(windowed, zoneByTarget, unique.size(), zoneIds.size(), unassigned);
        }

        private static CandidateSpot spot(
                String key,
                double originLng,
                double originLat,
                double eastM,
                double northM,
                FeatureType type,
                double weight,
                double depthM
        ) {
            UUID id = UUID.nameUUIDFromBytes(key.getBytes());
            CandidateSpot spot = new CandidateSpot();
            spot.setFeatureId(id);
            spot.setFishingTargetId(id);
            spot.setType(type);
            spot.setLocation(offset(originLng, originLat, eastM, northM));
            spot.setEntryPoint(spot.getLocation());
            spot.setExitPoint(spot.getLocation());
            spot.setStrategyWeight(weight);
            spot.setFeatureConfidence(0.82);
            spot.setRepresentativeDepthM(depthM);
            return spot;
        }

        private static Point offset(double lng, double lat, double eastM, double northM) {
            double dLat = northM / 111_320.0;
            double dLng = eastM / (111_320.0 * Math.cos(Math.toRadians(lat)));
            Point point = FACTORY.createPoint(new Coordinate(lng + dLng, lat + dLat));
            point.setSRID(4326);
            return point;
        }
    }
}
