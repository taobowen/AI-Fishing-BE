# Production data validation

- generatedAt: `2026-09-18T20:59:12.041712Z`
- environment: `prod`
- apiBase: `https://api.onwaterguide.taobowen.com`
- pipeline: `GIS`
- overall: **FAIL**
- visualReview: VISUAL_REVIEW_PENDING
- fishing quality: FISHING_QUALITY_NOT_YET_FIELD_VALIDATED

Numbers below are observed from this run. Empty cells were not collected. Do not treat GIS READY as good fishing.

| Lake | Phase2 | Bathymetry | Phase3 | Features | Strategy | Plan | Warnings |
|---|---|---:|---|---:|---|---|---|
| Head Lake | 12 avail / 1 fail | 61 | READY | 74 | no | no species | 1 |
| Rice Lake | 13 avail / 0 fail | 281 | READY | 229 | no | no species | 1 |
| Lake Scugog | 13 avail / 0 fail | 7 | READY | 8 | no | no species | 1 |
| Lake Simcoe |  avail /  fail |  |  |  | no | no species | 0 |
| Balsam Lake | 12 avail / 1 fail | 103 | READY | 88 | no | no species | 1 |
| Pigeon Lake | 12 avail / 1 fail | 338 | READY | 234 | no | no species | 1 |
| Sturgeon Lake | 13 avail / 0 fail | 108 | READY | 75 | no | no species | 1 |
| Lake Couchiching | 12 avail / 1 fail | 51 | READY | 2 | no | no species | 1 |
| Canal Lake | 13 avail / 0 fail | 28 | READY | 18 | no | no species | 0 |
| Sparrow Lake | 12 avail / 1 fail | 47 | READY | 64 | no | no species | 0 |
| Lake Wilcox | 13 avail / 0 fail | 32 | READY | 18 | no | no species | 1 |
| Musselman's Lake | 13 avail / 0 fail | 23 | READY | 7 | no | no species | 0 |
| Preston Lake | 13 avail / 0 fail | 11 | READY | 7 | no | no species | 0 |
| Heart Lake | 13 avail / 0 fail | 14 | READY | 9 | no | no species | 0 |
| Professor's Lake | 0 avail / 0 fail |  | FAILED | 0 | no | no species | 0 |
| Island Lake Reservoir | 13 avail / 0 fail | 0 | READY | 2 | no | no species | 0 |
| Belwood Lake | 13 avail / 0 fail | 7 | READY | 17 | no | no species | 0 |
| Mountsberg Reservoir | 10 avail / 0 fail | 0 | READY | 0 | no | no species | 0 |
| Christie Lake | 10 avail / 0 fail | 0 | READY | 1 | no | no species | 0 |
| Buckhorn Lake | 12 avail / 1 fail | 469 | READY | 110 | no | no species | 0 |

## IDENTITY

- **Head Lake** ogfId=551154028 officialName=Head Lake identityResolved=True boundaryPresent=True areaM2=9384005.601769188 tz=America/Toronto
- **Rice Lake** ogfId=124215626 officialName=Rice Lake identityResolved=True boundaryPresent=True areaM2=92321984.42509851 tz=America/Toronto
- **Lake Scugog** ogfId=260759954 officialName=Lake Scugog identityResolved=True boundaryPresent=True areaM2=65458350.22001915 tz=America/Toronto
- **Lake Simcoe** ogfId=851703532 officialName=Lake Simcoe identityResolved=True boundaryPresent=None areaM2=None tz=None
- **Balsam Lake** ogfId=1252543749 officialName=Balsam Lake identityResolved=True boundaryPresent=True areaM2=47652322.94454065 tz=America/Toronto
- **Pigeon Lake** ogfId=1253437644 officialName=Pigeon Lake identityResolved=True boundaryPresent=True areaM2=48620106.46896975 tz=America/Toronto
- **Sturgeon Lake** ogfId=1253432451 officialName=Sturgeon Lake identityResolved=True boundaryPresent=True areaM2=43877168.93437254 tz=America/Toronto
- **Lake Couchiching** ogfId=70653509 officialName=Lake Couchiching identityResolved=True boundaryPresent=True areaM2=45399442.27117261 tz=America/Toronto
- **Canal Lake** ogfId=1251066762 officialName=Canal Lake identityResolved=True boundaryPresent=True areaM2=8461838.880582392 tz=America/Toronto
- **Sparrow Lake** ogfId=114307154 officialName=Sparrow Lake identityResolved=True boundaryPresent=True areaM2=10502695.123153359 tz=America/Toronto
- **Lake Wilcox** ogfId=127162680 officialName=Wilcox Lake identityResolved=True boundaryPresent=True areaM2=558579.3002353996 tz=America/Toronto
- **Musselman's Lake** ogfId=1200800558 officialName=Musselman Lake identityResolved=True boundaryPresent=True areaM2=483720.44184386486 tz=America/Toronto
- **Preston Lake** ogfId=127163150 officialName=Preston Lake identityResolved=True boundaryPresent=True areaM2=318683.34817273595 tz=America/Toronto
- **Heart Lake** ogfId=127163933 officialName=Heart Lake identityResolved=True boundaryPresent=True areaM2=181714.72524655692 tz=America/Toronto
- **Professor's Lake** ogfId=None officialName=None identityResolved=False boundaryPresent=False areaM2=None tz=America/Toronto
- **Island Lake Reservoir** ogfId=851206721 officialName=Island Lake identityResolved=True boundaryPresent=True areaM2=1535169.8287159726 tz=America/Toronto
- **Belwood Lake** ogfId=127431693 officialName=Lake Belwood identityResolved=True boundaryPresent=True areaM2=6145815.924922111 tz=America/Toronto
- **Mountsberg Reservoir** ogfId=167698346 officialName=Mountsberg Reservoir identityResolved=True boundaryPresent=True areaM2=364417.8450708352 tz=America/Toronto
- **Christie Lake** ogfId=155931008 officialName=Christie Reservoir identityResolved=True boundaryPresent=True areaM2=312588.0739594747 tz=America/Toronto
- **Buckhorn Lake** ogfId=1253432521 officialName=Buckhorn Lake identityResolved=True boundaryPresent=True areaM2=31307734.3053405 tz=America/Toronto

## Lakes

### Head Lake

- id: `44444444-4444-4444-4444-444444444444`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 551154028
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: LARGEMOUTH_BASS, MUSKELLUNGE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=1 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=1 exactDupGroups=0
- importSeconds: None
- processSeconds: None
- planSeconds: None
- planError: None
- artifacts: `{}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

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
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, NORTHERN_PIKE, PANFISH, RAINBOW_TROUT, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=1 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=1 exactDupGroups=0
- importSeconds: None
- processSeconds: None
- planSeconds: None
- planError: None
- artifacts: `{}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=7 pages=1 raw=14 transferLimitObserved=False paginationComplete=True warning=None
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
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=1 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=1 exactDupGroups=0
- importSeconds: None
- processSeconds: None
- planSeconds: None
- planError: None
- artifacts: `{}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

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
- gisReady: False
- ogfId: None
- timezone: None
- boundaryPresent: None
- mappedSpecies: (none)
- configuredSpecies: None (None)
- empiricalColdStart: None catchEvents=None effortSegments=None
- geometry QA: invalid=None outsideBoundary=None polygonAbsurd=None lineAbsurd=None pointsSkippedArea=None exactDupGroups=None
- importSeconds: None
- processSeconds: None
- planSeconds: None
- planError: None
- artifacts: `{}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):


### Balsam Lake

- id: `44444444-4444-4444-4444-444444444448`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 1252543749
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, NORTHERN_PIKE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=2 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=2 exactDupGroups=0
- importSeconds: None
- processSeconds: 298
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444448/features.geojson", "sha256": "62a9ece59804f63739ee890ad71fdb0721462dbbf3c0644f5654328b8cc21fd2", "bytes": 7060443}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444448/map.png", "sha256": "231331284ca846885c5e56b7270f65f9efebd7784a4b01f7a8846d31b22e0dc2", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=3 pages=1 raw=5 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=3 pages=1 raw=3 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=103 pages=1 raw=103 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=51 pages=1 raw=51 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=247 pages=1 raw=17 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=83 pages=1 raw=152 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=761 pages=1 raw=761 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` FAILED records=None pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Invalid GeoJSON

### Pigeon Lake

- id: `44444444-4444-4444-4444-444444444449`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 1253437644
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, NORTHERN_PIKE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=4 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 438
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444449/features.geojson", "sha256": "dd3e11c496afc9e92fe9f4acfe336e71a0e9f8ca4ab6fbc3c14c5ee4b5184f4e", "bytes": 1626467}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444449/map.png", "sha256": "808eb4009bcb31529437c99ef0859aab9af4556e5722e88787a3fef7c26dee15", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=2 pages=1 raw=7 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=7 pages=1 raw=7 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=338 pages=1 raw=338 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=86 pages=1 raw=86 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=363 pages=1 raw=35 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=3 pages=1 raw=3 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=187 pages=1 raw=429 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=1397 pages=1 raw=1397 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` FAILED records=None pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Invalid GeoJSON

### Sturgeon Lake

- id: `44444444-4444-4444-4444-444444444450`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 1253432451
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, NORTHERN_PIKE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=1 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=1 exactDupGroups=0
- importSeconds: None
- processSeconds: 259
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444450/features.geojson", "sha256": "cf1c75de20b13bdc2f874fe173bdc88b9dc00e9f9ced9fbe44104500eb023575", "bytes": 2661190}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444450/map.png", "sha256": "0cd2b884bbe1ca1b8e0375b30f80c58042f8e63aff5e9530b07a0a53c308465b", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=10 pages=1 raw=15 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=3 pages=1 raw=3 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=108 pages=1 raw=108 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=54 pages=1 raw=54 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=957 pages=1 raw=61 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=4 pages=1 raw=4 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=237 pages=1 raw=350 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=1846 pages=1 raw=1846 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=1364 pages=1 raw=1364 transferLimitObserved=False paginationComplete=True warning=None

### Lake Couchiching

- id: `44444444-4444-4444-4444-444444444451`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 70653509
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LAKE_TROUT, LARGEMOUTH_BASS, MUSKELLUNGE, NORTHERN_PIKE, PANFISH, RAINBOW_TROUT, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=2 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=2 exactDupGroups=0
- importSeconds: None
- processSeconds: 81
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444451/features.geojson", "sha256": "6392c30da03cf76cf095c8290f38c5b33ce4fb002921aa9b5a87d42da4edbda0", "bytes": 811}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444451/map.png", "sha256": "d03bda2a51597131b447bc70cc785485a820d8a729d3196a29ea3a9e0ee9f1b0", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=17 pages=1 raw=27 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` FAILED records=None pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Invalid GeoJSON
- `BATHYMETRY_LINE` AVAILABLE records=51 pages=1 raw=51 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=10 pages=1 raw=10 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=402 pages=1 raw=35 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=3 pages=1 raw=3 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=125 pages=1 raw=230 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=1015 pages=1 raw=1015 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=668 pages=1 raw=668 transferLimitObserved=False paginationComplete=True warning=None

### Canal Lake

- id: `44444444-4444-4444-4444-444444444452`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 1251066762
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, NORTHERN_PIKE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 82
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444452/features.geojson", "sha256": "b2092fd1d5e485392c019f4e41e44993f7b432b904ab08c384e24f8418af3f6a", "bytes": 37693}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444452/map.png", "sha256": "d637287961030797563e43bb799f554cdc9d26425eef72bd71fcc7ff3c870e20", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=28 pages=1 raw=28 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=125 pages=1 raw=12 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=51 pages=1 raw=96 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=573 pages=1 raw=573 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=770 pages=1 raw=770 transferLimitObserved=False paginationComplete=True warning=None

### Sparrow Lake

- id: `44444444-4444-4444-4444-444444444453`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 114307154
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, NORTHERN_PIKE, PANFISH, RAINBOW_TROUT, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 132
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444453/features.geojson", "sha256": "0543f1b8f14ef77d622340386d97b68aa8f5718e06202bf70aefb7e25a95d495", "bytes": 1639359}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444453/map.png", "sha256": "d9ab567191ae93df81f72001bb051e099f0249df82fd523a427db7062fc6fda7", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=6 pages=1 raw=7 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=4 pages=1 raw=4 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=47 pages=1 raw=47 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=4 pages=1 raw=4 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=172 pages=1 raw=48 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=165 pages=1 raw=284 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=755 pages=1 raw=755 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` FAILED records=None pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Invalid GeoJSON

### Lake Wilcox

- id: `44444444-4444-4444-4444-444444444454`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 127162680
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, NORTHERN_PIKE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=1 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=1 exactDupGroups=0
- importSeconds: None
- processSeconds: 83
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444454/features.geojson", "sha256": "35795e53bfb59296b6a63799ef502740e786556a5e91f4aa32f9366e34f467cd", "bytes": 100637}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444454/map.png", "sha256": "b83e5e080bbd2400cd84d000ce97c858d744e5b47b85103d7174c3555164ed32", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=3 pages=1 raw=3 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=32 pages=1 raw=32 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=432 pages=1 raw=18 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=16 pages=1 raw=16 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=187 pages=1 raw=187 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=65 pages=1 raw=65 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=349 pages=1 raw=349 transferLimitObserved=False paginationComplete=True warning=None

### Musselman's Lake

- id: `44444444-4444-4444-4444-444444444455`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 1200800558
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, NORTHERN_PIKE, PANFISH, RAINBOW_TROUT, SMALLMOUTH_BASS, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 84
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444455/features.geojson", "sha256": "0ad90beb45dbc82086159e0e02dd03b6e811aee321e9ace6801e9bae022ab743", "bytes": 36836}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444455/map.png", "sha256": "7eb0f103eb47f01a45bc9f4a5803238af97edc71573dced9613c2a316dcb8b12", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=5 pages=1 raw=5 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=23 pages=1 raw=23 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=197 pages=1 raw=12 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=29 pages=1 raw=33 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=107 pages=1 raw=107 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=212 pages=1 raw=212 transferLimitObserved=False paginationComplete=True warning=None

### Preston Lake

- id: `44444444-4444-4444-4444-444444444456`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 127163150
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, LARGEMOUTH_BASS, NORTHERN_PIKE, PANFISH, RAINBOW_TROUT, SMALLMOUTH_BASS, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 71
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444456/features.geojson", "sha256": "6e4286e91322324a0d07a56c6f66c824db0b02fe269c6d0b39222ff89f7a1cb3", "bytes": 31399}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444456/map.png", "sha256": "2eb38f48fbe5fe6035457e025d66d54e14f3527248c154b4bdea1dbe8315f1ca", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=11 pages=1 raw=11 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=523 pages=1 raw=39 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=101 pages=1 raw=102 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=162 pages=1 raw=162 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=364 pages=1 raw=364 transferLimitObserved=False paginationComplete=True warning=None

### Heart Lake

- id: `44444444-4444-4444-4444-444444444457`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 127163933
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: CRAPPIE, LARGEMOUTH_BASS, NORTHERN_PIKE, PANFISH, RAINBOW_TROUT, SMALLMOUTH_BASS, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 58
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444457/features.geojson", "sha256": "661b515019741dad057993d05483c59aa21cdb4c07ef7d8e5c12e2460506fbf2", "bytes": 53308}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444457/map.png", "sha256": "6ab6537bae4a4835f7664d01dfaf43579c7b9572aa9d9df3ae39c8f149f37ab1", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=14 pages=1 raw=14 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=691 pages=1 raw=39 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=13 pages=1 raw=13 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=57 pages=1 raw=58 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=98 pages=1 raw=98 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=123 pages=1 raw=123 transferLimitObserved=False paginationComplete=True warning=None

### Professor's Lake

- id: `44444444-4444-4444-4444-444444444458`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: False
- ogfId: None
- timezone: America/Toronto
- boundaryPresent: False
- mappedSpecies: (none)
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 71
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444458/features.geojson", "sha256": "ed778c73ea51338d6576fb5992b189f2b94d9f3d5e199f46c1af520d6b0b3e6c", "bytes": 42}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444458/map.png", "sha256": "db2ecb0405bd275e44dbb20b18530740867a53708ee1cf2f2a7708c3b8a86e96", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):


### Island Lake Reservoir

- id: `44444444-4444-4444-4444-444444444459`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 851206721
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, NORTHERN_PIKE, PANFISH, RAINBOW_TROUT, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 71
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444459/features.geojson", "sha256": "1975032feb869505f072f3b3dce8790eb71a441957aa601c06650b0270d5e0e8", "bytes": 2825}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444459/map.png", "sha256": "13c467265b3b311b22de3c81380a0e8be0e687a2c306f8909b88fdbda5d1aece", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=282 pages=1 raw=18 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=4 pages=1 raw=4 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=21 pages=1 raw=46 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=340 pages=1 raw=340 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=191 pages=1 raw=191 transferLimitObserved=False paginationComplete=True warning=None

### Belwood Lake

- id: `44444444-4444-4444-4444-444444444460`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 127431693
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, LARGEMOUTH_BASS, NORTHERN_PIKE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=4 exactDupGroups=0
- importSeconds: None
- processSeconds: 100
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444460/features.geojson", "sha256": "208723c48fd81e4b6c5606885d38253ba099c270734d245a116e17dea7382fe1", "bytes": 311955}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444460/map.png", "sha256": "13680d48d9080b4c1964921c996b3000b80989f4944b1283f139a176f8e03170", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=7 pages=1 raw=7 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=6 pages=1 raw=6 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=477 pages=1 raw=33 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=144 pages=1 raw=157 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=265 pages=1 raw=265 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=173 pages=1 raw=173 transferLimitObserved=False paginationComplete=True warning=None

### Mountsberg Reservoir

- id: `44444444-4444-4444-4444-444444444461`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 167698346
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: BROOK_TROUT, CRAPPIE, LARGEMOUTH_BASS, NORTHERN_PIKE, PANFISH, RAINBOW_TROUT, SMALLMOUTH_BASS, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 72
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444461/features.geojson", "sha256": "ed778c73ea51338d6576fb5992b189f2b94d9f3d5e199f46c1af520d6b0b3e6c", "bytes": 42}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444461/map.png", "sha256": "9d2733a82e688c8a54783b3138ccb2af2da3ac1fb88188eb19c3032740b5eb55", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` NOT_AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Bathymetry index has no coverage for this lake
- `BATHYMETRY_LINE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Bathymetry index has no coverage for this lake
- `BATHYMETRY_POINT` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Bathymetry index has no coverage for this lake
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=500 pages=1 raw=23 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=47 pages=1 raw=49 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=71 pages=1 raw=71 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=115 pages=1 raw=115 transferLimitObserved=False paginationComplete=True warning=None

### Christie Lake

- id: `44444444-4444-4444-4444-444444444462`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 155931008
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: CRAPPIE, LARGEMOUTH_BASS, NORTHERN_PIKE, PANFISH, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 73
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444462/features.geojson", "sha256": "aa9157f783cf960edc2485cf10a2cbc53c51eb6873bf04ebb2c075a07617f96f", "bytes": 1197}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444462/map.png", "sha256": "223d7267e9bf368edf929646495de08560d8c75bb1f55c56d5076bd14e574a2b", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` NOT_AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Bathymetry index has no coverage for this lake
- `BATHYMETRY_LINE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Bathymetry index has no coverage for this lake
- `BATHYMETRY_POINT` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Bathymetry index has no coverage for this lake
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=324 pages=1 raw=15 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=3 pages=1 raw=3 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=102 pages=1 raw=110 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=253 pages=1 raw=253 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` AVAILABLE records=71 pages=1 raw=71 transferLimitObserved=False paginationComplete=True warning=None

### Buckhorn Lake

- id: `44444444-4444-4444-4444-444444444463`
- status: **FAIL**
- technical: NOT_VALID
- gisReady: True
- ogfId: 1253432521
- timezone: America/Toronto
- boundaryPresent: True
- mappedSpecies: CRAPPIE, LARGEMOUTH_BASS, MUSKELLUNGE, PANFISH, SMALLMOUTH_BASS, WALLEYE, YELLOW_PERCH
- configuredSpecies: None (None)
- empiricalColdStart: True catchEvents=0 effortSegments=0
- geometry QA: invalid=0 outsideBoundary=0 polygonAbsurd=0 lineAbsurd=0 pointsSkippedArea=0 exactDupGroups=0
- importSeconds: None
- processSeconds: 486
- planSeconds: None
- planError: None
- artifacts: `{"features.geojson": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444463/features.geojson", "sha256": "d8331bae8092490e2963811b1018a7385a9868dd8a9c9411ac3802680ac33544", "bytes": 3232364}, "map.png": {"path": "/Users/a89373/Github/AI-Fishing/AI-Fishing-BE/build/validation-artifacts/prod/44444444-4444-4444-4444-444444444463/map.png", "sha256": "c2c159d433778fbdb529761a617df19a1b160c7efae41bd2a0f82f771a29eb3b", "bytes": 186}}`

Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):

- `ACCESS_POINT` AVAILABLE records=0 pages=1 raw=3 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_INDEX` AVAILABLE records=11 pages=1 raw=11 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_LINE` AVAILABLE records=469 pages=1 raw=469 transferLimitObserved=False paginationComplete=True warning=None
- `BATHYMETRY_POINT` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `BOTTOM_SUBSTRATE` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario bottom-substrate product wired for lake ingest
- `FISH_HABITAT` AVAILABLE records=58 pages=1 raw=58 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_SPECIES` AVAILABLE records=295 pages=1 raw=16 transferLimitObserved=False paginationComplete=True warning=None
- `FISH_STOCKING` AVAILABLE records=0 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
- `FMZ` AVAILABLE records=2 pages=1 raw=2 transferLimitObserved=False paginationComplete=True warning=None
- `ISLAND` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `LAKE_BOUNDARY` AVAILABLE records=1 pages=1 raw=1 transferLimitObserved=False paginationComplete=True warning=None
- `REGULATION` PARTIAL records=1 pages=1 raw=0 transferLimitObserved=False paginationComplete=True warning=None
  - error: Regulations stored as catalogue/raw text only; no spatial sanctuary geometry
- `SHORELINE` AVAILABLE records=73 pages=1 raw=328 transferLimitObserved=False paginationComplete=True warning=None
- `VEGETATION` NOT_AVAILABLE records=0 pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: No official Ontario vegetation raster/product wired for lake ingest
- `WATERWAY` AVAILABLE records=736 pages=1 raw=736 transferLimitObserved=False paginationComplete=True warning=None
- `WETLAND` FAILED records=None pages=None raw=None transferLimitObserved=None paginationComplete=None warning=None
  - error: Invalid GeoJSON

## Operations

- S3/CloudWatch/RDS size: **NOT_RUN** (local-live uses `file:` raw storage under `./data/raw`).
- Cognito user-vs-admin split: **CAN FIELD TEST WITH WARNING** — `DevAuthenticationFilter` grants `ROLE_ADMIN` to every `X-User-Id` when `app.admin.enabled=true`. True split requires prod Cognito `ADMIN` group.
- ALB idle timeout / Simcoe over 60s: **EXTERNAL PREREQUISITE** (no ALB in this workspace run). Local Simcoe import was 46s; GIS process was 873s.
- Device/GPS/MapLibre: **UNVERIFIED** (no preview build against this `API_BASE`).
- HTTPS frontend against deployed API: **EXTERNAL PREREQUISITE**.
- `generatePlan()` in the app still POSTs `{}`.

## Blockers

- EXTERNAL PREREQUISITE: AWS was never deployed from this workspace (private S3 HEAD, CloudWatch 5xx/OOM, Cognito admin split, ALB 15-minute idle timeout, HTTPS FE).
- OPENAI_API_KEY was unset on the local-live API process; user `POST /plan` returned VALIDATION_ERROR and no StrategyRun/TripPlan was persisted.
- FIELD-TESTING: visual QA of GeoJSON/PNG in gitignored `build/validation-artifacts` (`visualReview: VISUAL_REVIEW_PENDING`).
- FUTURE DATA IMPROVEMENT: vegetation / bottom substrate `NOT_AVAILABLE` by design; wetland GeoJSON parse FAILED on live LIO for some lakes.
