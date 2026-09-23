package com.aifishing.guidance.empirical;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GetHistoricalPerformanceParams;
import com.aifishing.guidance.contracts.HistoricalPerformance;
import com.aifishing.guidance.contracts.SeasonBucket;
import com.aifishing.guidance.contracts.WindBucket;
import com.aifishing.guidance.contracts.WindDirectionBucket;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HistoricalPerformanceQuery {

    private final HistoricalContributionStore contributionStore;
    private final LakeRepository lakeRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final GuidanceProperties properties;
    private final Clock clock;

    public HistoricalPerformanceQuery(
            HistoricalContributionStore contributionStore,
            LakeRepository lakeRepository,
            TripWaypointRepository tripWaypointRepository,
            GuidanceProperties properties,
            Clock clock
    ) {
        this.contributionStore = contributionStore;
        this.lakeRepository = lakeRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<HistoricalPerformance> lookup(GetHistoricalPerformanceParams params) {
        if (params == null || params.lakeId() == null || params.targetSpecies() == null) {
            return Optional.empty();
        }
        Lake lake = lakeRepository.findById(params.lakeId()).orElse(null);
        ZoneId zone = EmpiricalAlgorithm.zoneOf(lake == null ? null : lake.getTimeZoneId());
        SeasonBucket season = EmpiricalAlgorithm.seasonBucket(clock.instant(), zone);
        FeatureType structure = null;
        if (params.tripWaypointId() != null) {
            structure = tripWaypointRepository.findById(params.tripWaypointId())
                    .map(TripWaypoint::getFeatureType)
                    .orElse(null);
        }
        for (BackoffLevel level : levels(params, season, structure)) {
            EmpiricalRawCounts raw = contributionStore.sumMatching(level.match);
            if (raw.isEmpty()) {
                continue;
            }
            EmpiricalAlgorithm.Derived derived = EmpiricalAlgorithm.derive(raw, properties.getEmpirical());
            if (properties.getEmpirical().insufficient(raw.contributingSessionWaypointCount(), derived.sampleConfidence())
                    && !level.equals(last(params, season, structure))) {
                continue;
            }
            EmpiricalGrain grain = new EmpiricalGrain(
                    params.lakeId(),
                    null,
                    level.tripWaypointId,
                    params.targetSpecies(),
                    season,
                    null,
                    level.structure,
                    level.windBucket,
                    level.windDirectionBucket,
                    level.lureFamily,
                    EmpiricalAlgorithm.VERSION
            );
            return Optional.of(EmpiricalAlgorithm.toHistorical(grain, raw, derived, clock.instant()));
        }
        return Optional.empty();
    }

    private List<BackoffLevel> levels(
            GetHistoricalPerformanceParams params,
            SeasonBucket season,
            FeatureType structure
    ) {
        List<BackoffLevel> levels = new ArrayList<>();
        UUID waypointId = params.tripWaypointId();
        FishSpecies species = params.targetSpecies();
        LureFamily lure = params.lureFamily();
        if (waypointId != null) {
            EmpiricalMatch.Builder finest = base(params.lakeId(), species, season)
                    .tripWaypointId(waypointId);
            if (lure != null) {
                finest.lureFamily(lure);
            }
            levels.add(new BackoffLevel(finest.build(), waypointId, null, null, null, lure));
            levels.add(new BackoffLevel(
                    base(params.lakeId(), species, season).tripWaypointId(waypointId).build(),
                    waypointId,
                    null,
                    null,
                    null,
                    null
            ));
        }
        if (structure != null) {
            levels.add(new BackoffLevel(
                    base(params.lakeId(), species, season).structure(structure).build(),
                    null,
                    structure,
                    null,
                    null,
                    null
            ));
        }
        levels.add(new BackoffLevel(
                base(params.lakeId(), species, season).build(),
                null,
                null,
                null,
                null,
                null
        ));
        return levels;
    }

    private BackoffLevel last(
            GetHistoricalPerformanceParams params,
            SeasonBucket season,
            FeatureType structure
    ) {
        List<BackoffLevel> levels = levels(params, season, structure);
        return levels.getLast();
    }

    private static EmpiricalMatch.Builder base(UUID lakeId, FishSpecies species, SeasonBucket season) {
        return EmpiricalMatch.builder(EmpiricalAlgorithm.VERSION)
                .lakeId(lakeId)
                .species(species)
                .seasonBucket(season);
    }

    private record BackoffLevel(
            EmpiricalMatch match,
            UUID tripWaypointId,
            FeatureType structure,
            WindBucket windBucket,
            WindDirectionBucket windDirectionBucket,
            LureFamily lureFamily
    ) {
    }
}
