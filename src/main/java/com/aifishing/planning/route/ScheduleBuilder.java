package com.aifishing.planning.route;

import com.aifishing.planning.service.PlanningContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ScheduleBuilder {

    private final TravelTimeEstimator travelTimeEstimator;

    public ScheduleBuilder(TravelTimeEstimator travelTimeEstimator) {
        this.travelTimeEstimator = travelTimeEstimator;
    }

    public List<PlannedStop> fit(
            List<PlannedStop> stops,
            Instant start,
            Instant end,
            int returnBuffer,
            PlanningContext context
    ) {
        if (stops.isEmpty()) {
            return List.of();
        }
        List<PlannedStop> current = new ArrayList<>(stops);
        for (int attempt = 0; attempt < 8; attempt++) {
            List<PlannedStop> rebuilt = replay(current, start, context);
            if (fits(rebuilt, end, returnBuffer, context)) {
                return rebuilt;
            }
            current = shrink(rebuilt, context.properties().getSchedule().getMinSpotMinutes());
            if (current.size() < rebuilt.size()) {
                continue;
            }
            boolean changed = false;
            for (int i = 0; i < current.size(); i++) {
                PlannedStop stop = current.get(i);
                if (stop.stayMinutes() > context.properties().getSchedule().getMinSpotMinutes()) {
                    current.set(i, copyStay(stop, stop.stayMinutes() - 5));
                    changed = true;
                }
            }
            if (!changed) {
                return replay(current, start, context);
            }
        }
        return replay(current, start, context);
    }

    private List<PlannedStop> replay(List<PlannedStop> stops, Instant start, PlanningContext context) {
        List<PlannedStop> rebuilt = new ArrayList<>();
        Instant cursor = start;
        for (PlannedStop stop : stops) {
            TravelEstimate travel = stop.fromPrevious();
            Instant arrival = cursor.plusSeconds(Math.round(travel.minutes() * 60));
            Instant departure = arrival.plus(Duration.ofMinutes(stop.stayMinutes()));
            rebuilt.add(new PlannedStop(
                    stop.candidate(),
                    arrival,
                    departure,
                    stop.stayMinutes(),
                    travel,
                    stop.timeScore(),
                    stop.whyThisTime(),
                    stop.environment(),
                    stop.precedingWaitMinutes(),
                    stop.precedingWaitLocation()
            ));
            cursor = departure;
        }
        return rebuilt;
    }

    private boolean fits(List<PlannedStop> stops, Instant end, int returnBuffer, PlanningContext context) {
        if (stops.isEmpty()) {
            return true;
        }
        PlannedStop last = stops.get(stops.size() - 1);
        Instant done = last.departureAt();
        if (context.accessKnown()) {
            TravelEstimate home = travelTimeEstimator.estimate(
                    last.candidate().spot().getLocation(),
                    context.routeStartPoint(),
                    context
            );
            done = done.plusSeconds(Math.round(home.minutes() * 60) + returnBuffer * 60L);
        }
        return !done.isAfter(end);
    }

    private List<PlannedStop> shrink(List<PlannedStop> stops, int minStay) {
        if (stops.size() > 1) {
            List<PlannedStop> copy = new ArrayList<>(stops);
            copy.remove(copy.size() - 1);
            return copy;
        }
        return stops;
    }

    private static PlannedStop copyStay(PlannedStop stop, int stay) {
        return new PlannedStop(
                stop.candidate(),
                stop.arrivalAt(),
                stop.departureAt(),
                stay,
                stop.fromPrevious(),
                stop.timeScore(),
                stop.whyThisTime(),
                stop.environment(),
                stop.precedingWaitMinutes(),
                stop.precedingWaitLocation()
        );
    }
}
