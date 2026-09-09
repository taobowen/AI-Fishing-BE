package com.aifishing.lake.ingestion;

import com.aifishing.lake.ingestion.dto.FeatureQuery;
import com.aifishing.lake.ingestion.dto.RawPage;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class OntarioFixtures {

    public static final long HEAD_OGF = 1001L;
    public static final long RICE_OGF = 1002L;
    public static final long SCUGOG_OGF = 1003L;
    public static final long SIMCOE_OGF = 1004L;

    public record LakeSpec(String name, long ogfId, double lat, double lng, int stockingCount, boolean bathymetry, boolean wetland) {
    }

    public static final LakeSpec HEAD = new LakeSpec("Head Lake", HEAD_OGF, 44.75, -78.92, 2, true, false);
    public static final LakeSpec RICE = new LakeSpec("Rice Lake", RICE_OGF, 44.18, -78.17, 1, false, false);
    public static final LakeSpec SCUGOG = new LakeSpec("Lake Scugog", SCUGOG_OGF, 44.15, -78.90, 0, false, false);
    public static final LakeSpec SIMCOE = new LakeSpec("Lake Simcoe", SIMCOE_OGF, 44.42, -79.37, 3, true, true);

    private OntarioFixtures() {
    }

    public static List<RawPage> pagesFor(FeatureQuery query) {
        LakeSpec lake = guessLake(query);
        String layer = layerKey(query.layerUrl());
        return switch (layer) {
            case "O1-25" -> List.of(page(1, waterbody(lake), query));
            case "O1-14" -> shorelinePages(lake, query);
            case "O1-10" -> List.of(page(1, island(lake), query));
            case "O1-26" -> List.of(page(1, watercourse(lake), query));
            case "O1-15" -> List.of(page(1, lake.wetland() ? wetland(lake) : emptyCollection(), query));
            case "O1-31" -> List.of(page(1, lake.bathymetry() ? bathymetryIndex(lake) : emptyCollection(), query));
            case "O1-30" -> List.of(page(1, bathymetryLine(lake), query));
            case "O1-27" -> List.of(page(1, bathymetryPoint(lake), query));
            case "O7-2" -> List.of(page(1, araSpecies(lake), query));
            case "O7-31" -> List.of(page(1, habitat(lake), query));
            case "O7-15" -> List.of(page(1, accessPoint(lake), query));
            case "O7-14" -> List.of(page(1, fmz(lake), query));
            case "STOCKING" -> List.of(page(1, stocking(lake), query));
            default -> List.of(page(1, emptyCollection(), query));
        };
    }

    public static List<RawPage> invalidGeometryPage(FeatureQuery query) {
        String body = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","id":9,"properties":{"OGF_ID":9},"geometry":{"type":"Polygon","coordinates":[[[0,0],[1,1]]]}}
                ]}
                """;
        return List.of(page(1, body, query));
    }

    public static String emptyCollection() {
        return """
                {"type":"FeatureCollection","features":[]}
                """;
    }

    public static String waterbody(LakeSpec lake) {
        return featureCollection(polygonFeature(lake.ogfId(), lake.name(), lake.lng(), lake.lat(), """
                "WATERBODY_TYPE":"Lake","OFFICIAL_NAME":"%s","MUNICIPALITY":"Kawartha"
                """.formatted(lake.name())));
    }

    public static String twoRiceLakes() {
        LakeSpec south = RICE;
        String north = polygonFeature(1099L, "Rice Lake", -80.05, 46.10, """
                "WATERBODY_TYPE":"Lake","OFFICIAL_NAME":"Rice Lake","MUNICIPALITY":"Northern"
                """);
        String southFeature = polygonFeature(south.ogfId(), south.name(), south.lng(), south.lat(), """
                "WATERBODY_TYPE":"Lake","OFFICIAL_NAME":"Rice Lake","MUNICIPALITY":"Northumberland"
                """);
        return """
                {"type":"FeatureCollection","features":[%s,%s]}
                """.formatted(southFeature, north);
    }

    public static RawPage page(int index, String body, FeatureQuery query) {
        return new RawPage(
                index,
                body.getBytes(StandardCharsets.UTF_8),
                query.layerUrl() + "/query",
                Map.of(
                        "resultOffset", index == 1 ? 0 : 1,
                        "outSR", 4326,
                        "where", query.where() == null ? "1=1" : query.where()
                ),
                200,
                MediaType.APPLICATION_JSON_VALUE
        );
    }

    public static RawPage pageWithLimit(int index, String body, FeatureQuery query, boolean exceeded) {
        String payload = body.replace("\"FeatureCollection\"", exceeded
                ? "\"FeatureCollection\",\"exceededTransferLimit\":true"
                : "\"FeatureCollection\"");
        return page(index, payload, query);
    }

    private static List<RawPage> shorelinePages(LakeSpec lake, FeatureQuery query) {
        String page1 = """
                {"type":"FeatureCollection","exceededTransferLimit":true,"features":[%s]}
                """.formatted(lineFeature(lake.ogfId() * 10, lake.lng(), lake.lat(), "\"NAME\":\"%s shore A\"".formatted(lake.name())));
        String page2 = """
                {"type":"FeatureCollection","features":[%s]}
                """.formatted(lineFeature(lake.ogfId() * 10 + 1, lake.lng() + 0.01, lake.lat(), "\"NAME\":\"%s shore B\"".formatted(lake.name())));
        return List.of(page(1, page1, query), page(2, page2, query));
    }

    private static String island(LakeSpec lake) {
        return featureCollection(polygonFeature(lake.ogfId() * 20, lake.name() + " Island", lake.lng() - 0.005, lake.lat() + 0.005, """
                "WATERBODY_TYPE":"Island","NAME":"%s Island"
                """.formatted(lake.name())));
    }

    private static String watercourse(LakeSpec lake) {
        return featureCollection(lineFeature(lake.ogfId() * 30, lake.lng(), lake.lat() - 0.03, "\"NAME\":\"%s Creek\"".formatted(lake.name())));
    }

    private static String wetland(LakeSpec lake) {
        return featureCollection(polygonFeature(lake.ogfId() * 40, lake.name() + " Wetland", lake.lng() + 0.01, lake.lat() - 0.01, """
                "WETLAND_TYPE":"Marsh","NAME":"%s Marsh"
                """.formatted(lake.name())));
    }

    private static String bathymetryIndex(LakeSpec lake) {
        return featureCollection(polygonFeature(lake.ogfId() * 50, lake.name() + " Bathy", lake.lng(), lake.lat(), """
                "SURVEY_METHOD":"sonar","YEAR":2018
                """));
    }

    private static String bathymetryLine(LakeSpec lake) {
        return featureCollection(lineFeature(lake.ogfId() * 60, lake.lng(), lake.lat(), """
                "DEPTH":10,"UNIT":"m"
                """));
    }

    private static String bathymetryPoint(LakeSpec lake) {
        return """
                {"type":"FeatureCollection","features":[{
                  "type":"Feature","id":%d,"properties":{"OGF_ID":%d,"DEPTH_FT":32.8,"UNIT":"ft"},
                  "geometry":{"type":"Point","coordinates":[%s,%s]}
                }]}
                """.formatted(lake.ogfId() * 70, lake.ogfId() * 70, lake.lng(), lake.lat());
    }

    private static String araSpecies(LakeSpec lake) {
        return featureCollection(polygonFeature(lake.ogfId() * 80, lake.name(), lake.lng(), lake.lat(), """
                "FISH_SPECIES_SUMMARY":"Walleye, Smallmouth Bass, Cisco","WATERBODY_LID":"WB-%d","OFFICIAL_WATERBODY_NAME":"%s","FMZ":15
                """.formatted(lake.ogfId(), lake.name())));
    }

    private static String stocking(LakeSpec lake) {
        StringBuilder features = new StringBuilder();
        for (int i = 0; i < lake.stockingCount(); i++) {
            if (i > 0) {
                features.append(',');
            }
            features.append("""
                    {"type":"Feature","id":%d,"properties":{"OBJECTID":%d,"SPECIES":"Walleye","YEAR":%d,"QUANTITY":1000},
                     "geometry":{"type":"Point","coordinates":[%s,%s]}}
                    """.formatted(lake.ogfId() * 90 + i, lake.ogfId() * 90 + i, 2020 + i, lake.lng(), lake.lat()));
        }
        return """
                {"type":"FeatureCollection","features":[%s]}
                """.formatted(features);
    }

    private static String habitat(LakeSpec lake) {
        return featureCollection(polygonFeature(lake.ogfId() * 110, lake.name() + " spawn", lake.lng(), lake.lat(), """
                "ACTIVITY_TYPE":"Spawning","SPECIES":"Walleye"
                """));
    }

    private static String accessPoint(LakeSpec lake) {
        long nearId = lake.ogfId() * 120;
        long farId = lake.ogfId() * 121;
        return """
                {"type":"FeatureCollection","features":[{
                  "type":"Feature","id":%d,"properties":{
                    "OGF_ID":%d,
                    "SITE_NAME":"%s Launch",
                    "FISHING_ACCESS_POINT_TYPE":"Boat Launch",
                    "PARKING_PRESENCE_FLG":"Yes",
                    "SITE_OWNERSHIP_TYPE":"Municipal",
                    "MATERIAL_TYPE":"Concrete",
                    "ACCESSIBILITY_FLG":"Unknown"
                  },
                  "geometry":{"type":"Point","coordinates":[%s,%s]}
                },{
                  "type":"Feature","id":%d,"properties":{
                    "OGF_ID":%d,
                    "SITE_NAME":"%s Far Launch",
                    "FISHING_ACCESS_POINT_TYPE":"Boat Launch",
                    "PARKING_PRESENCE_FLG":"No",
                    "SITE_OWNERSHIP_TYPE":"Municipal"
                  },
                  "geometry":{"type":"Point","coordinates":[%s,%s]}
                }]}
                """.formatted(
                nearId, nearId, lake.name(), lake.lng() + 0.01, lake.lat(),
                farId, farId, lake.name(), lake.lng() + 0.20, lake.lat()
        );
    }

    private static String fmz(LakeSpec lake) {
        return featureCollection(polygonFeature(lake.ogfId() * 130, "FMZ 15", lake.lng(), lake.lat(), """
                "ZONE":"15","NAME":"FMZ 15"
                """));
    }

    private static String featureCollection(String feature) {
        return """
                {"type":"FeatureCollection","features":[%s]}
                """.formatted(feature);
    }

    private static String polygonFeature(long ogfId, String name, double lng, double lat, String extraProps) {
        double pad = 0.02;
        return """
                {"type":"Feature","id":%d,"properties":{"OGF_ID":%d,"NAME":"%s",%s},
                 "geometry":{"type":"Polygon","coordinates":[[
                   [%s,%s],[%s,%s],[%s,%s],[%s,%s],[%s,%s]
                 ]]}}
                """.formatted(
                ogfId, ogfId, name, extraProps,
                lng - pad, lat - pad,
                lng + pad, lat - pad,
                lng + pad, lat + pad,
                lng - pad, lat + pad,
                lng - pad, lat - pad
        );
    }

    private static String lineFeature(long ogfId, double lng, double lat, String extraProps) {
        return """
                {"type":"Feature","id":%d,"properties":{"OGF_ID":%d,%s},
                 "geometry":{"type":"LineString","coordinates":[[%s,%s],[%s,%s]]}}
                """.formatted(ogfId, ogfId, extraProps, lng, lat, lng + 0.01, lat + 0.01);
    }

    public static String layerKey(String url) {
        if (url.contains("FishStocking")) {
            return "STOCKING";
        }
        int slash = url.lastIndexOf('/');
        String id = slash >= 0 ? url.substring(slash + 1) : url;
        if (url.contains("LIO_Open01")) {
            return "O1-" + id;
        }
        if (url.contains("LIO_Open07")) {
            return "O7-" + id;
        }
        return url;
    }

    public static LakeSpec guessLake(FeatureQuery query) {
        if (query.where() != null && query.where().startsWith("OGF_ID=")) {
            long ogfId = Long.parseLong(query.where().substring("OGF_ID=".length()));
            return byOgf(ogfId);
        }
        if (query.minLat() == null) {
            return HEAD;
        }
        double lat = (query.minLat() + query.maxLat()) / 2;
        double lng = (query.minLng() + query.maxLng()) / 2;
        LakeSpec best = HEAD;
        double bestDist = dist(best, lat, lng);
        for (LakeSpec spec : List.of(RICE, SCUGOG, SIMCOE)) {
            double d = dist(spec, lat, lng);
            if (d < bestDist) {
                best = spec;
                bestDist = d;
            }
        }
        return best;
    }

    private static LakeSpec byOgf(long ogfId) {
        for (LakeSpec spec : List.of(HEAD, RICE, SCUGOG, SIMCOE)) {
            if (spec.ogfId() == ogfId) {
                return spec;
            }
        }
        return HEAD;
    }

    private static double dist(LakeSpec spec, double lat, double lng) {
        double dLat = spec.lat() - lat;
        double dLng = spec.lng() - lng;
        return dLat * dLat + dLng * dLng;
    }
}
