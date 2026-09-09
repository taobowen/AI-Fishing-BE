# Boat launch selection (Phase 8.7)

Boat trips start from exactly one of three modes: **AUTO_RECOMMENDED**, **OFFICIAL_SELECTED**, or **CUSTOM_SELECTED**. Shore trips do not use this UI. The model never invents launch coordinates. Canonical `lake_access_points` rows are never written by users.

Eligible official launches for the picker and `GET /boat-launches` are associated rows with `boat_launch = true`, including `PRIVATE`. AUTO does **not** use that set as-is.

Canonical membership (inside unioned lake water, or within `app.ingestion.access.max-lake-association-meters`, default 300 m) is not the same as AUTO eligibility or routability. The 80 m Phase 5 SHORE candidate filter is unrelated.

## Ownership

| `ownership_type` | Picker / OFFICIAL_SELECTED | AUTO |
| --- | --- | --- |
| `MUNICIPAL` / `PROVINCIAL` / `FEDERAL` / `PUBLIC` | Yes, if water-anchor works | Yes, if water-anchor works |
| `UNKNOWN` | Yes, with `OWNERSHIP_UNVERIFIED` | Yes, with that warning and lower `accessConfidence` (0.55 vs 0.85) |
| `PRIVATE` | Yes, with `PRIVATE_LAUNCH_PERMISSION_REQUIRED`. Listing is not permission | Never |

AUTO eligible conceptually: associated + `boatLaunch` + not `PRIVATE` + routable water-side `routeStartPoint`.

## Modes

| Mode | Source of truth | Route origin |
| --- | --- | --- |
| `AUTO_RECOMMENDED` | Server `LaunchRecommender` after launch-independent candidates | Water-side `routeStartPoint` of the chosen official launch |
| `OFFICIAL_SELECTED` | User-picked `officialAccessPointId` on this lake | Same water-anchor as AUTO; the GIS point is not mutated |
| `CUSTOM_SELECTED` | User tap, server-snapped once on create/update | Persisted `routeStartPoint` |

A missing `trip_launch_selections` row on a BOAT trip is AUTO. SHORE create/update ignores and clears the row.

## APIs (authenticated, not admin)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/lakes/{lakeId}/boat-launches` | `boat_launch=true` only, including PRIVATE. `ownershipType` + warnings. `routable` means a water-side origin exists. 404 if the lake is missing |
| POST | `/api/v1/lakes/{lakeId}/boat-launches/custom-preview` | Requested `{lat,lng}` + optional `boatId`. Does **not** persist. Recomputes snap server-side |
| POST/PATCH | `/api/v1/trips` nested `launchSelection` | Server writes CUSTOM shore / route / `resolutionVersion`. Client-submitted shore/route points are not source of truth |

Typed 400s: `CUSTOM_LAUNCH_TOO_FAR_FROM_SHORE`, `CUSTOM_LAUNCH_ROUTE_ANCHOR_UNAVAILABLE`, `LAUNCH_SELECTION_CONFLICT`. Nearby official suggestions never auto-switch mode.

## Custom snap (projected meters)

Persist GeoJSON/JTS in EPSG:4326. Distance, buffer, closest-point, and water-anchor run in a **local UTM metric CRS** (`LocalMetricCrs`, zone from lake longitude). Do not use degree-buffer approximations for 3 / 8 / 15 m offsets.

1. Selected lake water only (union of all `lake_boundaries`, else `lakes.boundary`, minus ISLAND waterways).
2. Nearest shoreline in meters. On land, snap farther than `app.launch.custom.max-snap-meters` (250) → too-far. In-water taps still resolve.
3. MultiPolygon: component that covers an in-water request, else nearest. `routeStartPoint` stays in that component.
4. Interior ring / island → allow + `CUSTOM_LAUNCH_ISLAND_SHORE`.
5. Water anchor: inward metric buffer 15 m, then 8 m, then 3 m; closest interior point to shore. If the buffer collapses, fail — **never centroid**.
6. Official GIS coordinates get the same water-anchor. `lake_access_points.location` is not updated.
7. Nearby official: known boat launches (`boat_launch=true`, including PRIVATE) within `nearby-official-meters`.

Generate Plan uses the persisted CUSTOM `routeStartPoint`. Re-snap only if the user changes launch, the stored resolution is invalid against current water, or `resolutionVersion` migrates. A material move (`material-move-meters`) returns `CUSTOM_LAUNCH_MOVED` and requires re-confirmation.

Privacy: snap/warnings at DEBUG; coordinates are not logged at INFO. Custom points never enter strategy prompts, analytics, Expo Router params, or `lake_access_points`.

## AUTO recommendation

Candidates stay launch-independent. After regulation / safety / geometry filters (not `BoatCapabilityFilter` or ranking `travelAccess`), `LaunchRecommender` scores **intrinsic fishing quality** (no launch proximity) plus per-launch travel/range. Tie-break is existing name order. Then BoatCapabilityFilter, full rank, and `RoutePlanner` run from `routeStartPoint`.

- No AUTO-allowed `boat_launch=true` rows (none, or only PRIVATE) → `NO_KNOWN_BOAT_LAUNCH`
- AUTO-allowed launches exist but none produce a water-side origin → `NO_ROUTABLE_KNOWN_BOAT_LAUNCH`

These codes stay distinct. The UI may offer custom launch in both cases. Reports must not collapse them.

`GeneratePlanRequest.accessPointId` is a **run-scoped official override only when the trip is AUTO**. It does not persist. OFFICIAL/CUSTOM + a conflicting id → 400 `LAUNCH_SELECTION_CONFLICT`.

## Snapshots

PlanningRun `inputSnapshot` and TripPlan `metadata` record mode, official id/source, CUSTOM points, verification, `resolutionVersion`, AUTO score summary, and warnings. Changing the trip launch does not rewrite existing plans; the next Generate Plan uses the new origin.

## Limitations

- Official ≠ open, public, safe, or legal.
- Custom ≠ a public ramp and is not legal advice.
- Heuristic travel is not marine routing. No Mapbox Search/Navigation, saved private launches, or social sharing.
