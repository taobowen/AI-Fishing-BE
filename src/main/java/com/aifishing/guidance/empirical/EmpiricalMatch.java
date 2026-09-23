package com.aifishing.guidance.empirical;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.guidance.contracts.SeasonBucket;
import com.aifishing.guidance.contracts.TimeBucket;
import com.aifishing.guidance.contracts.WindBucket;
import com.aifishing.guidance.contracts.WindDirectionBucket;
import com.aifishing.lake.processing.dto.FeatureType;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Contribution filter. Only {@link #constrained} dimensions are applied;
 * unconstrained dimensions are summed across.
 */
public record EmpiricalMatch(
        int algorithmVersion,
        UUID lakeId,
        UUID zoneId,
        UUID tripWaypointId,
        FishSpecies species,
        SeasonBucket seasonBucket,
        TimeBucket timeBucket,
        FeatureType structure,
        WindBucket windBucket,
        WindDirectionBucket windDirectionBucket,
        LureFamily lureFamily,
        Set<Dimension> constrained
) {

    public EmpiricalMatch {
        constrained = constrained == null || constrained.isEmpty()
                ? Set.of()
                : Set.copyOf(constrained);
    }

    public boolean constrains(Dimension dimension) {
        return constrained.contains(dimension);
    }

    public static Builder builder(int algorithmVersion) {
        return new Builder(algorithmVersion);
    }

    public static final class Builder {
        private final int algorithmVersion;
        private final EnumSet<Dimension> constrained = EnumSet.noneOf(Dimension.class);
        private UUID lakeId;
        private UUID zoneId;
        private UUID tripWaypointId;
        private FishSpecies species;
        private SeasonBucket seasonBucket;
        private TimeBucket timeBucket;
        private FeatureType structure;
        private WindBucket windBucket;
        private WindDirectionBucket windDirectionBucket;
        private LureFamily lureFamily;

        private Builder(int algorithmVersion) {
            this.algorithmVersion = algorithmVersion;
        }

        public Builder lakeId(UUID value) {
            this.lakeId = value;
            constrained.add(Dimension.LAKE);
            return this;
        }

        public Builder zoneId(UUID value) {
            this.zoneId = value;
            constrained.add(Dimension.ZONE);
            return this;
        }

        public Builder tripWaypointId(UUID value) {
            this.tripWaypointId = value;
            constrained.add(Dimension.WAYPOINT);
            return this;
        }

        public Builder species(FishSpecies value) {
            this.species = value;
            constrained.add(Dimension.SPECIES);
            return this;
        }

        public Builder seasonBucket(SeasonBucket value) {
            this.seasonBucket = value;
            constrained.add(Dimension.SEASON);
            return this;
        }

        public Builder timeBucket(TimeBucket value) {
            this.timeBucket = value;
            constrained.add(Dimension.TIME);
            return this;
        }

        public Builder structure(FeatureType value) {
            this.structure = value;
            constrained.add(Dimension.STRUCTURE);
            return this;
        }

        public Builder windBucket(WindBucket value) {
            this.windBucket = value;
            constrained.add(Dimension.WIND);
            return this;
        }

        public Builder windDirectionBucket(WindDirectionBucket value) {
            this.windDirectionBucket = value;
            constrained.add(Dimension.WIND_DIRECTION);
            return this;
        }

        public Builder lureFamily(LureFamily value) {
            this.lureFamily = value;
            constrained.add(Dimension.LURE);
            return this;
        }

        public EmpiricalMatch build() {
            return new EmpiricalMatch(
                    algorithmVersion,
                    lakeId,
                    zoneId,
                    tripWaypointId,
                    species,
                    seasonBucket,
                    timeBucket,
                    structure,
                    windBucket,
                    windDirectionBucket,
                    lureFamily,
                    constrained
            );
        }
    }

    public enum Dimension {
        LAKE,
        ZONE,
        WAYPOINT,
        SPECIES,
        SEASON,
        TIME,
        STRUCTURE,
        WIND,
        WIND_DIRECTION,
        LURE
    }
}
