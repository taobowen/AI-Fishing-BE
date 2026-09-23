package com.aifishing.planning.candidate;

/**
 * Search-space compression diagnostics. Not a business {@code RejectionReason}.
 * TIME_VARIANT_MERGED is not a rejected fishing opportunity.
 */
public enum CandidateCompressionReason {
    TRUE_DUPLICATE,
    SPATIAL_NEAR_REDUNDANCY,
    GEOMETRY_OVERLAP_REDUNDANCY,
    FEATURE_TYPE_BUDGET,
    REGIONAL_CANDIDATE_BUDGET,
    TIME_VARIANT_MERGED
}
