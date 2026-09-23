package com.aifishing.planning.spatial;

import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.environment.LocalOrientation;
import com.aifishing.planning.environment.TimeAdjustedSpotUtility;
import com.aifishing.planning.environment.TimeIndexedWeather;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.service.PlanningContext;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpatialUtility {

    private final TimeAdjustedSpotUtility pointUtility;

    public SpatialUtility(TimeAdjustedSpotUtility pointUtility) {
        this.pointUtility = pointUtility;
    }

    public double alongPath(
            RankedCandidate candidate,
            Instant arrival,
            int fishingMinutes,
            Point from,
            Point to,
            Geometry path,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation
    ) {
        RequestScoringCache scoring = RequestScoringCache.current();
        RequestScoringCache.AlongKey cacheKey = null;
        if (scoring != null && candidate != null && candidate.spot() != null && candidate.score() != null
                && candidate.score().breakdown() != null && context != null && context.properties() != null) {
            cacheKey = scoring.alongKey(
                    candidate, arrival, fishingMinutes, from, to, path, context, weather, orientation);
            Double cached = scoring.alongPath(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        double value = alongPathUncached(
                candidate, arrival, fishingMinutes, from, to, path, context, weather, orientation);
        if (scoring != null && cacheKey != null) {
            scoring.putAlongPath(cacheKey, value);
        }
        return value;
    }

    private double alongPathUncached(
            RankedCandidate candidate,
            Instant arrival,
            int fishingMinutes,
            Point from,
            Point to,
            Geometry path,
            PlanningContext context,
            TimeIndexedWeather weather,
            LocalOrientation orientation
    ) {
        List<Sample> samples = candidate.spot().getStaticSamples() != null && !candidate.spot().getStaticSamples().isEmpty()
                ? directed(candidate.spot().getStaticSamples(), from, to)
                : samples(path, from, to, context.properties().getSpatial());
        if (samples.isEmpty()) {
            return pointUtility.dwellValue(candidate, arrival, fishingMinutes, context, weather, orientation);
        }
        double decay = context.properties().getSchedule().getDwellDecay();
        double sum = 0;
        double weight = 0;
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            Instant at = arrival.plus(Duration.ofSeconds(Math.round(sample.fraction() * fishingMinutes * 60.0)));
            double utility = pointUtility.evaluateAt(candidate, at, sample.point(), context, weather, orientation, 0).utility();
            double w = Math.pow(decay, i);
            sum += utility * w;
            weight += w;
        }
        return weight == 0 ? 0 : sum / weight;
    }

    private static List<Sample> directed(List<Sample> samples, Point from, Point to) {
        if (samples.size() < 2 || from == null || to == null) {
            return samples;
        }
        double startToFirst = GeoMetrics.distanceM(from, samples.get(0).point());
        double startToLast = GeoMetrics.distanceM(from, samples.get(samples.size() - 1).point());
        if (startToLast + 1 < startToFirst) {
            List<Sample> reversed = new ArrayList<>();
            for (int i = samples.size() - 1; i >= 0; i--) {
                reversed.add(new Sample(samples.get(i).point(), 1.0 - samples.get(i).fraction()));
            }
            return reversed;
        }
        return samples;
    }

    public List<Sample> samples(Geometry path, Point from, Point to, PlanningProperties.Spatial spatial) {
        LineString line = asLine(path, from, to);
        if (line == null || line.getNumPoints() < 2) {
            List<Sample> fallback = new ArrayList<>();
            if (from != null) {
                fallback.add(new Sample(from, 0));
            }
            if (to != null && (from == null || GeoMetrics.distanceM(from, to) > 1)) {
                fallback.add(new Sample(to, 1));
            }
            return fallback;
        }
        double length = GeoMetrics.lengthM(line);
        int count = Math.min(spatial.getMaxSamplesPerGeometry(), Math.max(3, (int) Math.round(length / spatial.getSampleAlongM()) + 1));
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double fraction = count == 1 ? 0 : i / (double) (count - 1);
            samples.add(new Sample(along(line, fraction), fraction));
        }
        return samples;
    }

    private static LineString asLine(Geometry path, Point from, Point to) {
        if (path instanceof LineString lineString && lineString.getNumPoints() >= 2) {
            return lineString;
        }
        if (from == null || to == null) {
            return null;
        }
        return from.getFactory().createLineString(new Coordinate[]{from.getCoordinate(), to.getCoordinate()});
    }

    private static Point along(LineString line, double fraction) {
        double target = GeoMetrics.lengthM(line) * fraction;
        double acc = 0;
        Coordinate[] coords = line.getCoordinates();
        for (int i = 1; i < coords.length; i++) {
            double seg = GeoMetrics.distanceM(coords[i - 1], coords[i], coords[i - 1].y);
            if (acc + seg >= target || i == coords.length - 1) {
                double t = seg == 0 ? 0 : (target - acc) / seg;
                t = Math.max(0, Math.min(1, t));
                Coordinate c = new Coordinate(
                        coords[i - 1].x + t * (coords[i].x - coords[i - 1].x),
                        coords[i - 1].y + t * (coords[i].y - coords[i - 1].y)
                );
                Point point = line.getFactory().createPoint(c);
                point.setSRID(line.getSRID());
                return point;
            }
            acc += seg;
        }
        return line.getEndPoint();
    }

    public record Sample(Point point, double fraction) {
    }
}
