package com.aifishing.planning.spatial;

import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.route.TravelEstimate;
import org.locationtech.jts.geom.Point;

import java.util.HashMap;
import java.util.Map;

/**
 * Exact static geometry for one Generate request. Feasibility, range, and
 * weather stay outside this cache. Keys are the endpoint coordinates the
 * current checks already use, not a coarser distance bucket.
 */
public final class RequestSpatialCache {

    private static final ThreadLocal<RequestSpatialCache> CURRENT = new ThreadLocal<>();

    private final Map<PairKey, Double> geodesic = new HashMap<>();
    private final Map<PairKey, Boolean> land = new HashMap<>();
    private final Map<ReturnKey, TravelEstimate> returns = new HashMap<>();
    private int geodesicHits;
    private int geodesicMisses;
    private int landHits;
    private int landMisses;
    private int returnHits;
    private int returnMisses;

    private RequestSpatialCache() {
    }

    public static void open() {
        CURRENT.set(new RequestSpatialCache());
    }

    public static void close() {
        CURRENT.remove();
    }

    public static RequestSpatialCache current() {
        return CURRENT.get();
    }

    public static double geodesicMeters(Point from, Point to) {
        if (from == null || to == null) {
            return 0;
        }
        RequestSpatialCache cache = CURRENT.get();
        if (cache == null) {
            return GeoMetrics.distanceM(from, to);
        }
        PairKey key = PairKey.of(from, to);
        Double cached = cache.geodesic.get(key);
        if (cached != null) {
            cache.geodesicHits++;
            return cached;
        }
        cache.geodesicMisses++;
        double meters = GeoMetrics.distanceM(from, to);
        cache.geodesic.put(key, meters);
        return meters;
    }

    public Boolean landCrossing(Point from, Point to) {
        PairKey key = PairKey.of(from, to);
        Boolean cached = land.get(key);
        if (cached != null) {
            landHits++;
            return cached;
        }
        landMisses++;
        return null;
    }

    public void storeLandCrossing(Point from, Point to, boolean crossing) {
        land.put(PairKey.of(from, to), crossing);
    }

    public TravelEstimate returnGeometry(Point from, Point launch, double speedKmh, double waterFactor, double landFactor) {
        ReturnKey key = returnKey(from, launch, speedKmh, waterFactor, landFactor);
        TravelEstimate cached = returns.get(key);
        if (cached != null) {
            returnHits++;
            return cached;
        }
        returnMisses++;
        return null;
    }

    public void storeReturnGeometry(
            Point from,
            Point launch,
            double speedKmh,
            double waterFactor,
            double landFactor,
            TravelEstimate estimate
    ) {
        if (estimate != null) {
            returns.put(returnKey(from, launch, speedKmh, waterFactor, landFactor), estimate);
        }
    }

    public int geodesicHits() {
        return geodesicHits;
    }

    public int geodesicMisses() {
        return geodesicMisses;
    }

    public int landHits() {
        return landHits;
    }

    public int landMisses() {
        return landMisses;
    }

    public int returnHits() {
        return returnHits;
    }

    public int returnMisses() {
        return returnMisses;
    }

    public int avoidedComputations() {
        return geodesicHits + landHits + returnHits;
    }

    public static double hitRate(int hits, int misses) {
        int total = hits + misses;
        if (total <= 0) {
            return 0;
        }
        return Math.round(hits * 1000.0 / total) / 10.0;
    }

    private static ReturnKey returnKey(Point from, Point launch, double speedKmh, double waterFactor, double landFactor) {
        return new ReturnKey(
                PairKey.of(from, launch),
                Double.doubleToLongBits(speedKmh),
                Double.doubleToLongBits(waterFactor),
                Double.doubleToLongBits(landFactor)
        );
    }

    private record PairKey(long ax, long ay, long bx, long by) {
        private static PairKey of(Point from, Point to) {
            return new PairKey(
                    Double.doubleToLongBits(from.getX()),
                    Double.doubleToLongBits(from.getY()),
                    Double.doubleToLongBits(to.getX()),
                    Double.doubleToLongBits(to.getY())
            );
        }
    }

    private record ReturnKey(PairKey pair, long speedBits, long waterFactorBits, long landFactorBits) {
    }
}
