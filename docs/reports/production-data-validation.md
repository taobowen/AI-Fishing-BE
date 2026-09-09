# Production data validation

- generatedAt: `2026-09-03T01:50:33.837675Z`
- environment: `local-live`
- apiBase: `http://127.0.0.1:8080`
- pipeline: `GIS`
- overall: **FAIL**
- visualReview: VISUAL_REVIEW_PENDING
- fishing quality: FISHING_QUALITY_NOT_YET_FIELD_VALIDATED

Numbers below are observed from this local-live run against real Ontario LIO. Empty cells were not collected. GIS READY is not a fishing-quality claim.

| Lake | Phase2 | Bathymetry | Phase3 | Features | Strategy | Plan | Warnings |
|---|---|---:|---|---:|---|---|---|
| Head Lake | 12 avail / 1 fail | 61 | READY | 73 | no | OPENAI_API_KEY / app.openai.api-key is not configured | 0 |
| Rice Lake | 13 avail / 0 fail | 281 | READY | 229 | no | OPENAI_API_KEY / app.openai.api-key is not configured | 1 |
| Lake Scugog | 13 avail / 0 fail | 7 | READY | 7 | no | OPENAI_API_KEY / app.openai.api-key is not configured | 0 |
| Lake Simcoe | 10 avail / 3 fail | 1993 | READY | 1067 | no | configured species LAKE_TROUT not in canonical mapped fish []; no fallback matched | 1 |

## IDENTITY

- **Head Lake** ogfId=551154028 officialName=Head Lake identityResolved=True boundaryPresent=True areaM2=9384005.601802384 tz=America/Toronto
- **Rice Lake** ogfId=124215626 officialName=Rice Lake identityResolved=True boundaryPresent=True areaM2=92321984.42509705 tz=America/Toronto
- **Lake Scugog** ogfId=260759954 officialName=Lake Scugog identityResolved=True boundaryPresent=True areaM2=65458350.2200336 tz=America/Toronto
- **Lake Simcoe** ogfId=851703532 officialName=Lake Simcoe identityResolved=True boundaryPresent=True areaM2=722692722.5497638 tz=America/Toronto

## Lakes

### Head Lake

- id: `44444444-4444-4444-4444-444444444444`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 551154028
- timezone: America/Toronto
- postgis: 3.5 USE_GEOS=1 USE_PROJ=1 USE_STATS=1
- boundaryPresent: True
- mappedSpecies: LARGEMOUTH_BASS, MUSKELLUNGE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: SMALLMOUTH_BASS (configured)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: 9
- processSeconds: 2
- planSeconds: 1
- planError: OPENAI_API_KEY / app.openai.api-key is not configured
- artifacts: `{"features.geojson": {"path": "build/validation-artifacts/local-live/44444444-4444-4444-4444-444444444444/features.geojson", "sha256": "92c309a20f9be6ade6066d945a802187f37d05f39fbedb03662ccb2288594dbb", "bytes": 129377}, "map.png": {"path": "build/validation-artifacts/local-live/44444444-4444-4444-4444-444444444444/map.png", "sha256": "c2492babadf8dde3edc77e88aafb7c144896d6a89b7a583017f93e660f0dd013", "bytes": 183}}`

Pagination (`pageCount` / `rawRecordCount` / `transferLimitObserved` / `paginationComplete` / `paginationWarning`):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=61 pages=1 raw=61 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=17 pages=1 raw=17 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=28 pages=1 raw=5 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=71 pages=1 raw=178 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=364 pages=1 raw=364 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` FAILED records=None pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Invalid GeoJSON

### Rice Lake

- id: `44444444-4444-4444-4444-444444444445`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 124215626
- timezone: America/Toronto
- postgis: 3.5 USE_GEOS=1 USE_PROJ=1 USE_STATS=1
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, NORTHERN_PIKE, PANFISH, RAINBOW_TROUT, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: WALLEYE (configured)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=1 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=1 exactDupGroups=0
- importSeconds: 23
- processSeconds: 134
- planSeconds: 1
- planError: OPENAI_API_KEY / app.openai.api-key is not configured
- artifacts: `{"features.geojson": {"path": "build/validation-artifacts/local-live/44444444-4444-4444-4444-444444444445/features.geojson", "sha256": "1e6b9e723b9b8a21c2014480ecae12c48f265846ec137b07a66b9ac229eb3ce2", "bytes": 6238874}, "map.png": {"path": "build/validation-artifacts/local-live/44444444-4444-4444-4444-444444444445/map.png", "sha256": "781c27e1f01f903ceb2aa8463eecb9eb901896309e9b7d3cd178e745cc2e2a4c", "bytes": 183}}`

Pagination (`pageCount` / `rawRecordCount` / `transferLimitObserved` / `paginationComplete` / `paginationWarning`):

- `ACCESS_POINT` AVAILABLE records=14 pages=1 raw=14 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=281 pages=1 raw=281 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=135 pages=1 raw=135 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=2968 pages=1 raw=282 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=6 pages=1 raw=6 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=1155 pages=1 raw=1359 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=1757 pages=1 raw=1757 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=1724 pages=1 raw=1724 transferLimitObserved=False paginationComplete=True warning=None

### Lake Scugog

- id: `44444444-4444-4444-4444-444444444446`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 260759954
- timezone: America/Toronto
- postgis: 3.5 USE_GEOS=1 USE_PROJ=1 USE_STATS=1
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: LARGEMOUTH_BASS (configured)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: 11
- processSeconds: 2
- planSeconds: 0
- planError: OPENAI_API_KEY / app.openai.api-key is not configured
- artifacts: `{"features.geojson": {"path": "build/validation-artifacts/local-live/44444444-4444-4444-4444-444444444446/features.geojson", "sha256": "5856335fd626034cd03196f5f19f280762114eaef31a4767599bd0e0b849fcfe", "bytes": 28053}, "map.png": {"path": "build/validation-artifacts/local-live/44444444-4444-4444-4444-444444444446/map.png", "sha256": "0a1a46bfc963281feb02d54c1db62e4efabff58270e0e0ce7f6da37c168187d8", "bytes": 183}}`

Pagination (`pageCount` / `rawRecordCount` / `transferLimitObserved` / `paginationComplete` / `paginationWarning`):

- `ACCESS_POINT` AVAILABLE records=10 pages=1 raw=10 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=3 pages=1 raw=3 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=7 pages=1 raw=7 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=92 pages=1 raw=92 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=1857 pages=1 raw=478 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=793 pages=1 raw=853 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=1703 pages=1 raw=1703 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=1286 pages=1 raw=1286 transferLimitObserved=False paginationComplete=True warning=None

### Lake Simcoe

- id: `44444444-4444-4444-4444-444444444447`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 851703532
- timezone: America/Toronto
- postgis: 3.5 USE_GEOS=1 USE_PROJ=1 USE_STATS=1
- boundaryPresent: True
- mappedSpecies: (none)
- configuredSpecies: None (none)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=4 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=4 exactDupGroups=0
- importSeconds: 46
- processSeconds: 873
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "build/validation-artifacts/local-live/44444444-4444-4444-4444-444444444447/features.geojson", "sha256": "7793dcacc5e9578796f417326794f73e498976c323115214f02be4f66026e13a", "bytes": 11952642}, "map.png": {"path": "build/validation-artifacts/local-live/44444444-4444-4444-4444-444444444447/map.png", "sha256": "d0144806488571d2c537fbcab3b99268add077fbdc00c45bb2d4871e715596d9", "bytes": 183}}`

Pagination (`pageCount` / `rawRecordCount` / `transferLimitObserved` / `paginationComplete` / `paginationWarning`):

- `ACCESS_POINT` AVAILABLE records=105 pages=1 raw=105 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` FAILED records=None pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Invalid GeoJSON
- `BATHYMETRY_LINE` AVAILABLE records=1993 pages=1 raw=1993 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=39075 pages=20 raw=39075 transferLimitObserved=True paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=124 pages=1 raw=124 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` FAILED records=None pages=1 raw=818 transferLimitObserved=False paginationComplete=True warning=None
  - error: could not execute statement [ERROR: duplicate key value violates unique constraint "idx_lake_fish_species_src"   Detail: Key (lake_id, provider, source_record_id, import_version, source_species_name)=(44444444-4444-4444-4444-444444444447, LIO, 851108483:Blacknose Dace, 673096f4-76cd-402e-8c6b-6dbdac
- `FISH_STOCKING` AVAILABLE records=19 pages=1 raw=19 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=8 pages=1 raw=8 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=1199 pages=1 raw=1465 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=1689 pages=1 raw=1689 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` FAILED records=None pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Invalid GeoJSON

## Operations

- S3/CloudWatch/RDS size: **NOT_RUN** (local-live `file:` prefix under `./data/raw`).
- Cognito user-vs-admin split: **CAN FIELD TEST WITH WARNING** — local `X-User-Id` receives `ROLE_ADMIN` when `app.admin.enabled=true`. True split needs prod Cognito group `ADMIN`.
- ALB idle timeout / Simcoe over 60s: **EXTERNAL PREREQUISITE**. This run had no ALB. Observed local Simcoe import **46s**, GIS process **873s** (would exceed the AWS 60s default if sent through an unset ALB).
- Device/GPS/Mapbox: **UNVERIFIED**.
- HTTPS frontend against deployed API: **EXTERNAL PREREQUISITE**.
- FE `generatePlan()` still POSTs `{}`.
- Admin map PNG endpoint returned `INTERNAL_ERROR` JSON (~183 bytes) for all four lakes; GeoJSON artifacts were written. `visualReview` remains VISUAL_REVIEW_PENDING.

## Blockers

- **EXTERNAL PREREQUISITE:** AWS was never deployed from this workspace (private S3 `raw/`/`derived/` HEAD, CloudWatch 5xx/OOM, Cognito admin split, ALB 15-minute idle timeout, HTTPS FE).
- **OPENAI_API_KEY** was unset on the local-live API; user `POST /plan` returned `VALIDATION_ERROR` and no StrategyRun/TripPlan was stored. Head/Rice/Scugog trips were created; Simcoe plan was skipped because FISH_SPECIES import FAILED (duplicate source_record_id) so mapped species was empty.
- **FIELD-TESTING:** visual QA of GeoJSON in gitignored `build/validation-artifacts`.
- **FUTURE DATA IMPROVEMENT:** vegetation / bottom substrate `NOT_AVAILABLE` by design; live wetland GeoJSON parse FAILED on some lakes.

Phase 9 field test should not start until at least Head Lake has a real-data Strategy + TripPlan in this report (`TECHNICALLY_VALID`).

## Phase 8.7 Head Lake launch modes

Live Generate Plan for AUTO / other official / custom was **not** collected on this run (OpenAI unset; Head `ACCESS_POINT` raw=0).

- Coverage: Head known boat launches on file = **0** → expected AUTO code `NO_KNOWN_BOAT_LAUNCH` if a plan were generated. That is **not** `NO_ROUTABLE_KNOWN_BOAT_LAUNCH`.
- Custom shoreline remains a product option; it is not a public ramp and is not legal advice.
- Template: [`head-lake-launch-selection-abc.md`](head-lake-launch-selection-abc.md).
