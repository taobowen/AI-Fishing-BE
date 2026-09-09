package com.aifishing.lake.ingestion.source;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.dto.FeatureQuery;

public final class LakeQuerySupport {

    /** Tight pad: a 0.2° window returns thousands of OHN waterbodies and LIO omits large target polygons without exceededTransferLimit. */
    public static final double IDENTITY_PAD_DEG = 0.05;
    public static final double DATASET_PAD_DEG = 0.02;

    private LakeQuerySupport() {
    }

    public static FeatureQuery intersecting(String layerUrl, Lake lake, Integer pageSize) {
        Bbox bbox = bbox(lake, DATASET_PAD_DEG);
        return new FeatureQuery(layerUrl, "1=1", bbox.minLng(), bbox.minLat(), bbox.maxLng(), bbox.maxLat(), pageSize);
    }

    public static FeatureQuery aroundCentroid(String layerUrl, Lake lake, Integer pageSize) {
        Bbox bbox = centroidPad(lake, IDENTITY_PAD_DEG);
        return new FeatureQuery(layerUrl, "1=1", bbox.minLng(), bbox.minLat(), bbox.maxLng(), bbox.maxLat(), pageSize);
    }

    public static FeatureQuery byOgfId(String layerUrl, long ogfId, Integer pageSize) {
        return new FeatureQuery(layerUrl, "OGF_ID=" + ogfId, null, null, null, null, pageSize);
    }

    public static Bbox bbox(Lake lake, double padDeg) {
        if (lake.getBboxMinLng() != null
                && lake.getBboxMinLat() != null
                && lake.getBboxMaxLng() != null
                && lake.getBboxMaxLat() != null) {
            return new Bbox(
                    lake.getBboxMinLng() - padDeg,
                    lake.getBboxMinLat() - padDeg,
                    lake.getBboxMaxLng() + padDeg,
                    lake.getBboxMaxLat() + padDeg
            );
        }
        return centroidPad(lake, Math.max(padDeg, 0.15));
    }

    private static Bbox centroidPad(Lake lake, double padDeg) {
        double lng = lake.getCentroid().getX();
        double lat = lake.getCentroid().getY();
        return new Bbox(lng - padDeg, lat - padDeg, lng + padDeg, lat + padDeg);
    }

    public record Bbox(double minLng, double minLat, double maxLng, double maxLat) {
    }
}
