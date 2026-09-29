package com.aifishing.planning.intent;

import com.aifishing.planning.domain.TripPlanningInputTarget;
import com.aifishing.planning.service.PlanningContext;

import java.util.List;

/**
 * Spatial shortlist for one frozen intent. Empty means the caller should keep the raw geometry.
 */
public interface IntentMatchSource {

    List<IntentMatch> matches(TripPlanningInputTarget target, PlanningContext context);
}
