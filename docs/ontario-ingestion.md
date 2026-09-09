# Ontario lake data ingestion (Phase 2)

Phase 2 adds an official Ontario acquisition layer on top of the Phase 1 lake catalog. Structure extraction (humps, drop-offs, `lake_features`) is Phase 3: [docs/lake-processing.md](lake-processing.md).

User `GET /api/v1/lakes` stays read-only and does not return ingestion details.

## Official sources

Layer IDs live in `application.yml` under `app.ontario.lio.*`. Adapters do not hard-code them.

LIO ArcGIS REST default CRS is NAD83 / EPSG:4269. Queries always send `outSR=4326` and `f=geojson`. If a geometry is still tagged 4269, `CrsTransformer` converts it locally.

### LIO Open01

`https://ws.lioservices.lrc.gov.on.ca/arcgis2/rest/services/LIO_OPEN_DATA/LIO_Open01/MapServer`

| Layer | Dataset |
| --- | --- |
| 25 OHN Waterbody | Lake identity + `LAKE_BOUNDARY` |
| 14 OHN Shoreline | `SHORELINE` |
| 10 OHN Hydrographic Poly | `ISLAND` |
| 26 OHN Watercourse | `WATERWAY` |
| 31 Bathymetry Index | `BATHYMETRY_INDEX` (coverage only) |
| 30 Bathymetry Line | `BATHYMETRY_LINE` |
| 27 Bathymetry Point | `BATHYMETRY_POINT` |
| 15 Wetland With Significance | `WETLAND` |

### LIO Open07

`https://ws.lioservices.lrc.gov.on.ca/arcgis2/rest/services/LIO_OPEN_DATA/LIO_Open07/MapServer`

| Layer | Dataset |
| --- | --- |
| 2 ARA Water Poly Segment | `FISH_SPECIES` |
| 31 Fish Activity Area | `FISH_HABITAT` |
| 15 Fishing Access Point | `ACCESS_POINT` |
| 14 Fisheries Management Zone | `FMZ` (status + raw; no dedicated table) |

### Other official

- Stocking: `https://services1.arcgis.com/TJH5KDher0W13Kgo/arcgis/rest/services/FishStockingDataForRecreationalPurposes/FeatureServer/0`
- Regulations: [Ontario recreational fishing regulations data](https://data.ontario.ca/dataset/recreational-fishing-regulations-data) — catalogue/raw only. **Not** a complete legal engine. No independent sanctuary REST layer was found, so status is `PARTIAL` and geometry is left empty. Natural-language sanctuary descriptions are **never** inferred into polygons.
- Fish ON-Line is a portal, not the download API.

`VEGETATION` and `BOTTOM_SUBSTRATE` have no wired official product → `NOT_AVAILABLE`. Garmin and substrate rasters are not invented.

## Flow

```text
ResolveLake (OGF_ID / source_lake_id, else centroid ∩ OHN Waterbody + name + lake type)
  → bbox/boundary written back onto the existing lake row (no duplicate lakes)
  → for each DatasetType (isolated try/catch)
       lastAttemptedAt = now; status IMPORTING
       fetch ArcGIS pages (resultOffset, max ~2000) → persist each raw page + request metadata + manifest
       normalize + validate ALL records
       if valid: TRANSACTION replace canonical (delete old for that lake/provider/dataset, insert new)
                 lastSuccessfulImportAt = now; AVAILABLE | PARTIAL
       if source absent: NOT_AVAILABLE (keep last good canonical)
       if fetch/normalize/validate fails: FAILED (keep last good canonical)
  → import summary
```

Identity is generic. Head Lake, Rice Lake, Lake Scugog, and Lake Simcoe use the same resolver and processors. OHN Waterbody names are read from `OFFICIAL_NAME_LABEL` (live LIO) as well as `OFFICIAL_NAME`. The identity envelope around the catalog centroid is 0.05° — a 0.2° window returns thousands of waterbodies and LIO can omit the target polygon without `exceededTransferLimit`. Name-only matching is not used; duplicate names are disambiguated by centroid distance (default 15 km). If identity is not unique, import stops and datasets are not downloaded.

`ImportJobRunner` is the sync entry used today. A worker (SQS/ECS) can replace it later without changing adapters.

## Raw storage

Paginated originals are first-class. Keys:

```text
raw/ontario/{lakeId}/{datasetType}/{importVersion}/page-0001.geojson
raw/ontario/{lakeId}/{datasetType}/{importVersion}/page-0002.geojson
raw/ontario/{lakeId}/{datasetType}/{importVersion}/manifest.json
```

Each `raw_data_objects` row stores source URL, query params (`resultOffset` / `resultRecordCount`), HTTP status, retrieved_at, SHA-256, page index, and storage URI.

Dev/test: `LocalFileRawDataStorage` → `app.raw.local-dir` (default `./data/raw`, tests use `./target/test-raw`). Prod: `S3RawDataStorage` (`app.raw.storage=s3`) using the task role and `app.s3.bucket`.

## Status semantics

`lake_dataset_status` is unique on `(lake_id, dataset_type, provider)`.

| Status | Meaning |
| --- | --- |
| `AVAILABLE` + `record_count = 0` | Query succeeded; this lake has no features of that type (not a missing source) |
| `NOT_AVAILABLE` | Official source is not available for this lake/type (no product, empty bathymetry index, 404 catalogue) |
| `FAILED` | Download, incomplete pagination, normalize, or validate failed. Canonical from the last **successful** import is kept. `lastAttemptedAt` updates; `lastSuccessfulImportAt` does not. |
| `PARTIAL` | Source exists but official data is incomplete (tabular regulations, no spatial sanctuary). Do not use PARTIAL as a stand-in for the three statuses above. |

Empty bathymetry index ⇒ `BATHYMETRY_INDEX`, `BATHYMETRY_LINE`, and `BATHYMETRY_POINT` are `NOT_AVAILABLE` without deleting last-good contours/points.

## Canonical tables

All Ontario-derived rows carry `provider`, `source_record_id`, `import_version`, `source_metadata`. Geometry is SRID 4326 with GIST indexes.

Dedicated types: bathymetry points (Point), contours (MultiLineString), access points (Point), lake boundaries (MultiPolygon). Mixed sources use PostGIS `geometry`: waterways, wetlands, habitats, restrictions.

`lake_fish_species.source_species_name` is required. Unmapped official names keep the source string and set `species = null`.

Depth is stored in meters (`DepthUnitConverter`; feet × 0.3048).

### Fishing access points (Open07 layer 15)

Live LIO fields (fixture aliases only as fallback):

| LIO property | Canonical |
| --- | --- |
| `FISHING_ACCESS_POINT_TYPE` = `Boat Launch` | `type=BOAT_LAUNCH`, `boat_launch=true` (do not set `shore_access`) |
| `FISHING_ACCESS_POINT_TYPE` = `Shoreline Access` | `type=SHORELINE_ACCESS`, `shore_access=true` (do not set `boat_launch`) |
| Other / missing type | `type=FISHING_ACCESS`; both flags stay null |
| `SITE_NAME` | `name` |
| `PARKING_PRESENCE_FLG` | `parking` (`Yes`/`No`; `Unknown` → null) |
| `SITE_OWNERSHIP_TYPE` | `ownership_type` `PRIVATE` \| `MUNICIPAL` \| `PROVINCIAL` \| `FEDERAL` \| `PUBLIC` \| `UNKNOWN` |

The entire raw properties object stays in `source_metadata`, including exact `SITE_OWNERSHIP_TYPE`, `MATERIAL_TYPE`, and `ACCESSIBILITY_FLG`.

Fetch still uses the lake envelope plus 0.02°. Canonical insert does **not** trust that envelope. A point is associated only if it is inside the **union of all** `lake_boundaries` (fallback `lakes.boundary`) or within 300 m in `LocalMetricCrs`. Distance is stored as `association_distance_meters`. Rejected bbox hits remain in `raw_data_objects` only. Successful refresh (including 0 associated rows) replaces the full canonical set for that lake/provider.

300 m association is not AUTO routability. PRIVATE never AUTO; UNKNOWN may AUTO with `OWNERSHIP_UNVERIFIED`. Do not attach the Haliburton village `"Head Lake"` launch to catalog Head (OGF `551154028`).

## Admin APIs

Enabled only when `app.admin.enabled=true` (dev and test profiles). Otherwise the controller is not registered (404).

`POST /api/v1/admin/lakes/validation-catalog` inserts the four documented lake UUIDs if missing (no reset). `POST /api/v1/admin/lakes/{id}/import` runs all datasets; `?dataset=ACCESS_POINT` refreshes only fishing access points and does not rewrite other canonical tables or their `importVersion`. Structure fingerprints (`sourceSnapshotId` on GIS/VISION/HYBRID) ignore `ACCESS_POINT` status. `GET /api/v1/admin/lakes/{id}/bootstrap-validation` is a read-only PostGIS sanity report. Dataset status includes pagination fields (`pageCount`, `rawRecordCount`, `transferLimitObserved`, `paginationComplete`, `paginationWarning`). Intermediate ArcGIS `exceededTransferLimit` is not a truncation warning. Operator script: [`docs/production-data-bootstrap.md`](production-data-bootstrap.md).

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Seed lake IDs (fixed UUIDs):

| Lake | UUID | Seed centroid |
| --- | --- | --- |
| Head Lake | `44444444-4444-4444-4444-444444444444` | 44.75, -78.92 |
| Rice Lake | `44444444-4444-4444-4444-444444444445` | 44.18, -78.17 |
| Lake Scugog | `44444444-4444-4444-4444-444444444446` | 44.15, -78.90 |
| Lake Simcoe | `44444444-4444-4444-4444-444444444447` | 44.42, -79.37 |

```bash
curl -X POST -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  http://localhost:8080/api/v1/admin/lakes/44444444-4444-4444-4444-444444444444/import

curl -X POST -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  "http://localhost:8080/api/v1/admin/lakes/44444444-4444-4444-4444-444444444444/import?dataset=ACCESS_POINT"

curl -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  http://localhost:8080/api/v1/admin/lakes/44444444-4444-4444-4444-444444444444/datasets

curl -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  "http://localhost:8080/api/v1/admin/lakes/data-summary?ids=44444444-4444-4444-4444-444444444444,44444444-4444-4444-4444-444444444445,44444444-4444-4444-4444-444444444446,44444444-4444-4444-4444-444444444447"
```

Live Ontario is not called by `mvn test` (classpath fixtures + mocked HTTP). `@Tag("live")` is excluded by Surefire.

## Four-lake coverage (fixture pipeline)

These counts come from the **generic fixture pipeline** in `LakeIngestionIT` (same adapters/processors for all four lakes; counts are allowed to differ). They are **not** live LIO counts. Fill live numbers after a manual import; do not invent them.

| Dataset | Head | Rice | Scugog | Simcoe |
| --- | ---: | ---: | ---: | ---: |
| LAKE_BOUNDARY | 1 | 1 | 1 | 1 |
| SHORELINE | 2 | 2 | 2 | 2 |
| ISLAND | 1 | 1 | 1 | 1 |
| WATERWAY | 1 | 1 | 1 | 1 |
| WETLAND | 0 AVAILABLE | 0 AVAILABLE | 0 AVAILABLE | 1 |
| BATHYMETRY_INDEX | 1 | NOT_AVAILABLE | NOT_AVAILABLE | 1 |
| BATHYMETRY_LINE | 1 | NOT_AVAILABLE | NOT_AVAILABLE | 1 |
| BATHYMETRY_POINT | 1 | NOT_AVAILABLE | NOT_AVAILABLE | 1 |
| FISH_SPECIES | 3 | 3 | 3 | 3 |
| FISH_STOCKING | 2 | 1 | 0 AVAILABLE | 3 |
| FISH_HABITAT | 1 | 1 | 1 | 1 |
| ACCESS_POINT | 1 associated (2 raw) | 1 associated (2 raw) | 1 associated (2 raw) | 1 associated (2 raw) |
| FMZ | 1 | 1 | 1 | 1 |
| REGULATION | PARTIAL | PARTIAL | PARTIAL | PARTIAL |
| VEGETATION | NOT_AVAILABLE | NOT_AVAILABLE | NOT_AVAILABLE | NOT_AVAILABLE |
| BOTTOM_SUBSTRATE | NOT_AVAILABLE | NOT_AVAILABLE | NOT_AVAILABLE | NOT_AVAILABLE |

Head Lake remains the primary acceptance narrative; delivery includes all four lakes on the same pipeline.

## Known gaps

- No official vegetation raster or bottom-substrate product wired.
- No independent spatial sanctuary / fishing-restriction geometry layer; regulations stay tabular/`PARTIAL`.
- OHN Hydrographic Line (Open01 layer 9) is not configured; waterways use OHN Watercourse (26).
- FMZ is stored as status + raw pages, not a dedicated canonical table.
- S3 is wired in prod (`app.raw.storage=s3`); local files remain the default for `dev`/`test`.
- Live four-lake counts must be captured from a real import; fixture counts above must not be treated as production GIS coverage.
