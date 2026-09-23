package com.aifishing.guidance.versions;

import com.aifishing.guidance.empirical.EmpiricalAlgorithm;

/**
 * Learning component of a resolved policy. {@code learningAlgorithmVersion} is
 * the formula/bucketing version. {@code learningSnapshotVersion} stays null
 * until a true immutable evidence snapshot exists — do not treat
 * {@link EmpiricalAlgorithm#VERSION} as a snapshot.
 */
public record LearningProfile(
        String learningAlgorithmVersion,
        String learningSnapshotVersion
) {
    public static final String EMPIRICAL_ALGORITHM_1 = "empirical-algorithm-1";

    public static LearningProfile empiricalAlgorithm1() {
        return new LearningProfile(String.valueOf(EmpiricalAlgorithm.VERSION), null);
    }
}
