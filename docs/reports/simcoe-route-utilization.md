# Simcoe route utilization diagnostic

Exact deterministic regression from `SimcoeShapedGeneratePlanRegressionTest`.
Compression is out of scope.

## Historical classification (pre visit-state fix)

**Classification: A** (frozen). Before opportunity state, ZONE coverage exhausted the region after one visit.
From the 2-stop far-basin end state every leftover star failed range; unused 211 minutes were idle at the ramp.
That diagnosis is kept here; it is not the current search behavior.

**Current classification: C**

Beam depth equals effectiveMaxStops=3. A third macro visit is never expanded, even though 37 minutes remain after the 2-stop winner.

Unused minutes formula: trip(7:00–15:00=480) − fishing(290) − travel(152.8) = **37**.

## 1. Winning route

- plannedLaunchDeparture: 07:00
- plannedReturnAt: 14:31:46
- return travel: 66.2 min / 6537 m
- reported routeUtility: 9.7425
- scheduleReserveMinutes: 13

### Stop 1
- visit kind: NEW_ZONE_VISIT
- visit/zone id: `zone:8faebd5c-bf43-3bba-b685-dad237ac9b96`
- kind: ZONE
- arrival: 07:03:56
- departure: 09:18:56
- dwell: 135 min (plannedFishing=120, internalTransit=10, zoneWait=5)
- travel from previous: 3.9 min / 388 m (detour 1.35, landCrossing=false)
- cumulative applied range: 0.52 km
- fishing utility (dwellValue): 2.3377
- travelCost: 0.0052, proximityBonus: 0.0000, landPenalty: 0.0000, boatWeatherPenalty: 0.0000
- estimated increment: 2.3324
- cumulative estimated utility: 2.3324
- ZoneSubPlan present: true
### Stop 2
- visit kind: NEW_ZONE_VISIT
- visit/zone id: `zone:d3f8d0a7-068c-3df2-8c27-5c3efd22bd72`
- kind: ZONE
- arrival: 10:00:39
- departure: 11:30:39
- dwell: 90 min (plannedFishing=80, internalTransit=6, zoneWait=4)
- travel from previous: 41.7 min / 4120 m (detour 1.35, landCrossing=false)
- cumulative applied range: 6.09 km
- fishing utility (dwellValue): 1.4456
- travelCost: 0.0400, proximityBonus: 0.0000, landPenalty: 0.0000, boatWeatherPenalty: 0.0000
- estimated increment: 1.4056
- cumulative estimated utility: 3.7380
- ZoneSubPlan present: true
### Stop 3
- visit kind: NEW_ATOMIC
- visit/zone id: `400eb89f-fa78-39f6-be85-dc66f8f46517`
- kind: POINT
- arrival: 11:55:35
- departure: 13:25:35
- dwell: 90 min (plannedFishing=90, internalTransit=0, zoneWait=0)
- travel from previous: 24.9 min / 2462 m (detour 1.35, landCrossing=false)
- cumulative applied range: 9.41 km
- fishing utility (dwellValue): 2.2609
- travelCost: 0.0332, proximityBonus: 0.0461, landPenalty: 0.0000, boatWeatherPenalty: 0.0000
- estimated increment: 2.2738
- cumulative estimated utility: 6.0118
- ZoneSubPlan present: false

Cumulative applied range including return: **18.24 km** of **16.0 km** usable.

## 2. Search limits

| metric | value |
|---|---|
| searchMode | FULL_ROUTE |
| effectiveMaxStops | 3 |
| maxFeasibleStops | 3 |
| expectedStops | 1 |
| minStops | 1 |
| beamDepthReached | 3 |
| visitOptions | 10 |
| beamWidth | 24 |
| expansions used/budget | 2942 / 8000 |
| time guard hit | false |
| SEARCH_BUDGET_REACHED | false |
| usableMinutes (resolver) | 393 |
| representativeDwellMinutes | 60 |

`effectiveMaxStops = clamp(maxFeasibleStops, minEffectiveStops=2, hardMaxStops=10)`.
`maxFeasibleStops` is a **pre-search estimate** from median dwell + inter-stop time/range, not the actual remaining budget after the winning route.

## 3. Third-stop expansions from the winning 2-stop state

Replay of RoutePlanner.expand checks from the **winning 2-stop end state**
(wait options × visit options). `effectiveMaxStops` is 3, so Beam is allowed to
try a third stop; `beamDepthReached=2` is the **winner size**, not a search-depth cap.

Tried combinations: 30

| reason | count |
|---|---:|
| already visited | 3 |
| spacing (150m) | 0 |
| unknown travel | 0 |
| weather travel | 0 |
| time infeasible (dwell past trip end) | 0 |
| return reserve | 0 |
| range exceeded | 2 |
| max leg too long | 2 |
| dwell options empty | 23 |
| negative/marginal utility | 0 |
| **positive feasible** | 0 |
| beam pruning | n/a (3-stop children from this state range-fail before scoring) |

| option | kind | wait | to-candidate | home | applied km | classification |
|---|---|---|---|---|---|---|
| 400eb89f-fa78-39f6-be85-dc66f8f46517 | POINT | wait 0 | 0 m / 0.0 min | home 66.2 min | 0.00 km one-way applied | ALREADY_VISITED |
| 8c3aeb85-38f9-34d9-be5b-8a0c82639b6c | POINT | wait 0 | 482 m / 4.9 min | home 70.9 min | 0.65 km one-way applied | DWELL_EMPTY |
| 11079d13-4257-3a89-9d83-e94d3d8fd34d | POINT | wait 0 | 768 m / 7.8 min | home 72.7 min | 1.04 km one-way applied | DWELL_EMPTY |
| 1d4b3db0-9b2a-3516-8e50-995cd62248ff | POINT | wait 0 | 1019 m / 10.3 min | home 73.8 min | 1.38 km one-way applied | DWELL_EMPTY |
| a20400a4-abf8-3770-82a1-105791178ce4 | POINT | wait 0 | 963 m / 9.8 min | home 75.6 min | 1.30 km one-way applied | DWELL_EMPTY |
| 010a77be-8695-30d6-964f-14704a721498 | POINT | wait 0 | 2125 m / 21.5 min | home 87.5 min | 2.87 km one-way applied | DWELL_EMPTY |
| zone:8faebd5c-bf43-3bba-b685-dad237ac9b96 | ZONE | wait 0 | 6345 m / 64.2 min | home 3.9 min | 8.57 km one-way applied | LEG_TOO_LONG |
| zone:8faebd5c-bf43-3bba-b685-dad237ac9b96 | ZONE | wait 0 | 6345 m / 64.2 min | home 3.9 min | 8.57 km one-way applied | LEG_TOO_LONG |
| zone:d3f8d0a7-068c-3df2-8c27-5c3efd22bd72 | ZONE | wait 0 | 2462 m / 24.9 min | home 43.0 min | 3.32 km one-way applied | RANGE_EXCEEDED |
| zone:d3f8d0a7-068c-3df2-8c27-5c3efd22bd72 | ZONE | wait 0 | 2462 m / 24.9 min | home 43.0 min | 3.32 km one-way applied | RANGE_EXCEEDED |
| 400eb89f-fa78-39f6-be85-dc66f8f46517 | POINT | wait 15 | 0 m / 0.0 min | home 66.2 min | 0.00 km one-way applied | ALREADY_VISITED |
| 8c3aeb85-38f9-34d9-be5b-8a0c82639b6c | POINT | wait 15 | 482 m / 4.9 min | home 70.9 min | 0.65 km one-way applied | DWELL_EMPTY |
| 11079d13-4257-3a89-9d83-e94d3d8fd34d | POINT | wait 15 | 768 m / 7.8 min | home 72.7 min | 1.04 km one-way applied | DWELL_EMPTY |
| 1d4b3db0-9b2a-3516-8e50-995cd62248ff | POINT | wait 15 | 1019 m / 10.3 min | home 73.8 min | 1.38 km one-way applied | DWELL_EMPTY |
| a20400a4-abf8-3770-82a1-105791178ce4 | POINT | wait 15 | 963 m / 9.8 min | home 75.6 min | 1.30 km one-way applied | DWELL_EMPTY |
| 010a77be-8695-30d6-964f-14704a721498 | POINT | wait 15 | 2125 m / 21.5 min | home 87.5 min | 2.87 km one-way applied | DWELL_EMPTY |
| zone:8faebd5c-bf43-3bba-b685-dad237ac9b96 | ZONE | wait 15 | 6345 m / 64.2 min | home 3.9 min | 8.57 km one-way applied | DWELL_EMPTY |
| zone:8faebd5c-bf43-3bba-b685-dad237ac9b96 | ZONE | wait 15 | 6345 m / 64.2 min | home 3.9 min | 8.57 km one-way applied | DWELL_EMPTY |
| zone:d3f8d0a7-068c-3df2-8c27-5c3efd22bd72 | ZONE | wait 15 | 2462 m / 24.9 min | home 43.0 min | 3.32 km one-way applied | DWELL_EMPTY |
| zone:d3f8d0a7-068c-3df2-8c27-5c3efd22bd72 | ZONE | wait 15 | 2462 m / 24.9 min | home 43.0 min | 3.32 km one-way applied | DWELL_EMPTY |
| 400eb89f-fa78-39f6-be85-dc66f8f46517 | POINT | wait 30 | 0 m / 0.0 min | home 66.2 min | 0.00 km one-way applied | ALREADY_VISITED |
| 8c3aeb85-38f9-34d9-be5b-8a0c82639b6c | POINT | wait 30 | 482 m / 4.9 min | home 70.9 min | 0.65 km one-way applied | DWELL_EMPTY |
| 11079d13-4257-3a89-9d83-e94d3d8fd34d | POINT | wait 30 | 768 m / 7.8 min | home 72.7 min | 1.04 km one-way applied | DWELL_EMPTY |
| 1d4b3db0-9b2a-3516-8e50-995cd62248ff | POINT | wait 30 | 1019 m / 10.3 min | home 73.8 min | 1.38 km one-way applied | DWELL_EMPTY |
| a20400a4-abf8-3770-82a1-105791178ce4 | POINT | wait 30 | 963 m / 9.8 min | home 75.6 min | 1.30 km one-way applied | DWELL_EMPTY |
| 010a77be-8695-30d6-964f-14704a721498 | POINT | wait 30 | 2125 m / 21.5 min | home 87.5 min | 2.87 km one-way applied | DWELL_EMPTY |
| zone:8faebd5c-bf43-3bba-b685-dad237ac9b96 | ZONE | wait 30 | 6345 m / 64.2 min | home 3.9 min | 8.57 km one-way applied | DWELL_EMPTY |
| zone:8faebd5c-bf43-3bba-b685-dad237ac9b96 | ZONE | wait 30 | 6345 m / 64.2 min | home 3.9 min | 8.57 km one-way applied | DWELL_EMPTY |
| zone:d3f8d0a7-068c-3df2-8c27-5c3efd22bd72 | ZONE | wait 30 | 2462 m / 24.9 min | home 43.0 min | 3.32 km one-way applied | DWELL_EMPTY |
| zone:d3f8d0a7-068c-3df2-8c27-5c3efd22bd72 | ZONE | wait 30 | 2462 m / 24.9 min | home 43.0 min | 3.32 km one-way applied | DWELL_EMPTY |

## 4. Remaining budgets after the 2-stop winner

| metric | value |
|---|---|
| time remaining after last departure | 94 min |
| return buffer | 15 min |
| idle until trip end after return (unused) | 37 min |
| usable round-trip range | 16.0 km |
| applied range used before last home | 9.41 km |
| remaining range before home | 6.59 km |
| home from last stop | 8.83 km / 66.2 min |
| remaining range after home | -2.24 km |

Positive feasible third visits: **0**.

## 5. PhysicalZone dwell semantics

ZONE dwell comes from `ZoneSubPlanner.packages()` at 45/90/135/180, not clipped by `maxSpotMinutes=90`.
RoutePlannerHarness now injects ZoneSubPlanner. EXTEND replaces the current zone package; REVISIT is a later re-entry.

### Zone `zone:8faebd5c-bf43-3bba-b685-dad237ac9b96`
- selected zone member count: 6
- harness RoutePlanner ZoneSubPlanner: **wired** (production-like)
- macro dwell assigned: 135 min (ZONE packages, not clipped by maxSpotMinutes=90)
- ZoneSubPlanner local fishing minutes at that dwell: 120
- local transit: 10, local wait: 5, micro-stops: 6
- local sequence: [HUMP@20min, HUMP@20min, POINT@20min, DROP_OFF@20min, FLAT@20min, DROP_OFF@20min]
- truncated by atomic dwell cap: false
### Zone `zone:d3f8d0a7-068c-3df2-8c27-5c3efd22bd72`
- selected zone member count: 4
- harness RoutePlanner ZoneSubPlanner: **wired** (production-like)
- macro dwell assigned: 90 min (ZONE packages, not clipped by maxSpotMinutes=90)
- ZoneSubPlanner local fishing minutes at that dwell: 80
- local transit: 6, local wait: 4, micro-stops: 4
- local sequence: [BASIN@20min, BASIN@20min, BASIN@20min, BASIN@20min]
- truncated by atomic dwell cap: false

## 6. Route objective vs unused time

pickBest orders by **highest totalValue**, then more stops, then first feature id.
Unused usable session time has **no opportunity cost** in the increment.
There is no unused-time penalty and no HomewardProgressBonus.

Opportunity Revisit Cooldown is a **future enhancement** (not implemented). `maxZoneEntries=2` is the temporary hard guard.

## Return-path fixture winning route (A/B/C/D/E)

Do not treat this sequence as a required winner. Macro stops=4, fishing=310, travel=86.8, unused=83, utility=10.0718, return=13:53:49.

### Macro 1 NEW_ZONE_VISIT
- identity: `cb7816f3-f912-37f3-b10f-9187ff2c8354`
- kind: ZONE
- dwell 135 (fish 120, local transit 8)
- members consumed: [dfa7de5f-d035-3c82-ac0f-6d86e7699c8a, 33895bd6-bdfc-31c1-8ed7-58caa5ea3d05, 92042cd2-23eb-3c37-ab3a-803c3985650b, 11e48552-8e1c-3af6-93e1-cb2e8834bb71, 9d3c5d2a-7b20-3155-9ef4-f7a94534d9b9, 2833d115-2154-3f7e-8702-dca031468e79]
- members remaining after this stop's package: []
- marginal package utility: 4.0552
- visitIncrement: 4.0500
- macro travel: 3.9 min
- local km: 0.876
### Macro 2 NEW_ZONE_VISIT
- identity: `bc41789e-8913-369b-b959-446eb8aa98e2`
- kind: ZONE
- dwell 90 (fish 80, local transit 6)
- members consumed: [41453cc6-30bd-368d-884b-2444de950c66, 3464fc94-5c56-3810-bbcd-63300f63146b, 9190c2b8-c777-39ca-9e07-4328e5406f75, eefb3e2d-6963-3b17-8b43-51778597655b]
- members remaining after this stop's package: []
- marginal package utility: 2.5102
- visitIncrement: 2.5611
- macro travel: 16.1 min
- local km: 0.377
### Macro 3 NEW_ZONE_VISIT
- identity: `67586e76-d850-3a8c-9e0f-1ab73bc1c342`
- kind: ZONE
- dwell 90 (fish 80, local transit 4)
- members consumed: [51f253ce-e0f3-33a6-8630-a3cdf8e0d915, 5c233064-ab9a-3818-b964-7f4658fe9037, cfc140f3-372a-3cc4-864e-aa32432d0bfc, d3f695c2-96c0-33a6-8ea5-2032236f4c6d]
- members remaining after this stop's package: []
- marginal package utility: 2.9968
- visitIncrement: 3.0433
- macro travel: 17.1 min
- local km: 0.319
### Macro 4 NEW_ATOMIC
- identity: `b92ef240-eabf-3cb8-a447-979bfea9be56`
- kind: PATH
- dwell 30 (fish 30, local transit 0)
- members consumed: []
- members remaining after this stop's package: []
- marginal package utility: 0.3842
- visitIncrement: 0.4175
- macro travel: 20.2 min
- local km: 0.000

Leftover identities not on the winner: [43fa465a-0daa-33d4-a0b7-4e1c19a814c7]. Not chosen because Beam picked a higher totalValue feasible route; leftover options remain representable (EXTEND/REVISIT/unvisited) rather than coverage-exhausted.

Future enhancement: Opportunity Revisit Cooldown is **not** implemented. `maxZoneEntries=2` is the temporary guard.
