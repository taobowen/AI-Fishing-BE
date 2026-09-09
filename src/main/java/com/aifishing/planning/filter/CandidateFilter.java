package com.aifishing.planning.filter;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.service.PlanningContext;

public interface CandidateFilter {

    FilterResult apply(CandidateSpot candidate, PlanningContext context);
}
