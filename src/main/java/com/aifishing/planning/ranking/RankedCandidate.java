package com.aifishing.planning.ranking;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.strategy.domain.DepthRange;

public record RankedCandidate(CandidateSpot spot, SpotScore score, DepthRange windowDepth) {
}
