package com.aifishing.guidance.empirical;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.guidance.contracts.SeasonBucket;
import com.aifishing.guidance.contracts.TimeBucket;
import com.aifishing.guidance.contracts.WindBucket;
import com.aifishing.guidance.contracts.WindDirectionBucket;
import com.aifishing.lake.processing.dto.FeatureType;

import java.util.Comparator;
import java.util.UUID;

/**
 * Finest observation grain. Null means unknown / not applicable and is part of the key.
 */
public record EmpiricalGrain(
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
        int algorithmVersion
) implements Comparable<EmpiricalGrain> {

    private static final Comparator<EmpiricalGrain> ORDER = Comparator
            .comparing(EmpiricalGrain::lakeId, EmpiricalGrain::compareNullable)
            .thenComparing(EmpiricalGrain::zoneId, EmpiricalGrain::compareNullable)
            .thenComparing(EmpiricalGrain::tripWaypointId, EmpiricalGrain::compareNullable)
            .thenComparing(EmpiricalGrain::species, EmpiricalGrain::compareEnum)
            .thenComparing(EmpiricalGrain::seasonBucket, EmpiricalGrain::compareEnum)
            .thenComparing(EmpiricalGrain::timeBucket, EmpiricalGrain::compareEnum)
            .thenComparing(EmpiricalGrain::structure, EmpiricalGrain::compareEnum)
            .thenComparing(EmpiricalGrain::windBucket, EmpiricalGrain::compareEnum)
            .thenComparing(EmpiricalGrain::windDirectionBucket, EmpiricalGrain::compareEnum)
            .thenComparing(EmpiricalGrain::lureFamily, EmpiricalGrain::compareEnum)
            .thenComparingInt(EmpiricalGrain::algorithmVersion);

    @Override
    public int compareTo(EmpiricalGrain other) {
        return ORDER.compare(this, other);
    }

    private static <T extends Comparable<T>> int compareNullable(T left, T right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private static <E extends Enum<E>> int compareEnum(E left, E right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.name().compareTo(right.name());
    }
}
