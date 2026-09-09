package com.aifishing.lake.ingestion.service;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.OntarioProperties;
import com.aifishing.lake.ingestion.dto.FeatureQuery;
import com.aifishing.lake.ingestion.dto.ParsedFeature;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.processor.CrsTransformer;
import com.aifishing.lake.ingestion.processor.FeatureProperties;
import com.aifishing.lake.ingestion.processor.GeoJsonFeatureParser;
import com.aifishing.lake.ingestion.processor.GeometrySupport;
import com.aifishing.lake.ingestion.source.LakeQuerySupport;
import com.aifishing.lake.ingestion.source.OntarioFeatureClient;
import com.aifishing.lake.repo.LakeRepository;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LakeIdentityResolver {

    private static final String[] OFFICIAL_NAME_KEYS = {
            "OFFICIAL_NAME_LABEL",
            "OFFICIAL_NAME",
            "GEOGRAPHIC_NAME",
            "NAME",
            "OFFICIAL_WATERBODY_NAME"
    };

    private final OntarioProperties properties;
    private final OntarioFeatureClient featureClient;
    private final GeoJsonFeatureParser parser;
    private final CrsTransformer crsTransformer;
    private final LakeRepository lakeRepository;

    public LakeIdentityResolver(
            OntarioProperties properties,
            OntarioFeatureClient featureClient,
            GeoJsonFeatureParser parser,
            CrsTransformer crsTransformer,
            LakeRepository lakeRepository
    ) {
        this.properties = properties;
        this.featureClient = featureClient;
        this.parser = parser;
        this.crsTransformer = crsTransformer;
        this.lakeRepository = lakeRepository;
    }

    @Transactional
    public Lake resolve(Lake lake) {
        OntarioProperties.Lio lio = properties.getLio();
        String waterbodyUrl = lio.layerUrl(lio.getOpen01Base(), lio.getWaterbodyLayer());
        Long knownOgfId = lake.getOgfId();
        if (knownOgfId == null) {
            knownOgfId = parseOgfId(lake.getSourceLakeId());
        }

        FeatureQuery query = knownOgfId != null
                ? LakeQuerySupport.byOgfId(waterbodyUrl, knownOgfId, properties.getPageSize())
                : LakeQuerySupport.aroundCentroid(waterbodyUrl, lake, properties.getPageSize());
        List<ParsedFeature> features = parseAll(featureClient.query(query));
        ParsedFeature match = knownOgfId != null
                ? uniqueByOgfId(features, knownOgfId, lake)
                : disambiguate(features, lake);
        apply(lake, match);
        return lakeRepository.save(lake);
    }

    private List<ParsedFeature> parseAll(List<RawPage> pages) {
        List<ParsedFeature> features = new ArrayList<>();
        for (RawPage page : pages) {
            features.addAll(parser.parse(page.body()));
        }
        return features;
    }

    private ParsedFeature uniqueByOgfId(List<ParsedFeature> features, long ogfId, Lake lake) {
        List<ParsedFeature> matches = features.stream()
                .filter(feature -> ogfId == FeatureProperties.longValue(feature.properties(), "OGF_ID"))
                .toList();
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (matches.isEmpty() && features.size() == 1) {
            return features.getFirst();
        }
        throw new IdentityResolutionException(
                "Could not uniquely resolve OHN waterbody OGF_ID " + ogfId + " for lake " + lake.getName()
        );
    }

    private ParsedFeature disambiguate(List<ParsedFeature> features, Lake lake) {
        Point centroid = lake.getCentroid();
        List<Scored> scored = new ArrayList<>();
        for (ParsedFeature feature : features) {
            if (feature.geometry() == null) {
                continue;
            }
            Geometry geometry = crsTransformer.toWgs84(feature.geometry(), feature.geometry().getSRID());
            String type = FeatureProperties.text(feature.properties(), "WATERBODY_TYPE", "FEATURE_TYPE", "TYPE");
            if (isExcludedWatercourse(type) && !geometry.covers(centroid) && !geometry.contains(centroid)) {
                continue;
            }
            double distanceKm = haversineKm(
                    centroid.getY(),
                    centroid.getX(),
                    geometry.getCentroid().getY(),
                    geometry.getCentroid().getX()
            );
            boolean contains = geometry.covers(centroid) || geometry.contains(centroid);
            boolean nameMatch = nameMatches(lake, feature);
            boolean lakeType = isLakeType(type);
            if (!contains && !nameMatch) {
                continue;
            }
            if (!contains && distanceKm > properties.getIdentityMaxDistanceKm()) {
                continue;
            }
            if (!contains && !lakeType) {
                continue;
            }
            scored.add(new Scored(feature, distanceKm, contains, nameMatch));
        }
        List<Scored> containing = scored.stream().filter(Scored::contains).toList();
        if (containing.size() == 1) {
            return containing.getFirst().feature();
        }
        if (containing.size() > 1) {
            return closestUnique(containing, lake);
        }
        List<Scored> named = scored.stream().filter(Scored::nameMatch).toList();
        if (named.size() == 1) {
            return named.getFirst().feature();
        }
        if (named.size() > 1) {
            return closestUnique(named, lake);
        }
        throw new IdentityResolutionException(
                "Could not uniquely resolve OHN waterbody identity for " + lake.getName()
                        + " using centroid + official name (not name-only)"
        );
    }

    private ParsedFeature closestUnique(List<Scored> candidates, Lake lake) {
        List<Scored> sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(Scored::distanceKm))
                .toList();
        Scored best = sorted.getFirst();
        if (sorted.size() > 1) {
            double second = sorted.get(1).distanceKm();
            if (Math.abs(best.distanceKm() - second) < 0.05) {
                throw new IdentityResolutionException(
                        "Ambiguous OHN waterbody matches for " + lake.getName() + " at similar centroid distance"
                );
            }
        }
        if (best.distanceKm() > properties.getIdentityMaxDistanceKm() && !best.contains()) {
            throw new IdentityResolutionException(
                    "Nearest named match for " + lake.getName() + " exceeds identity distance threshold"
            );
        }
        return best.feature();
    }

    private void apply(Lake lake, ParsedFeature match) {
        Geometry geometry = crsTransformer.toWgs84(match.geometry(), match.geometry().getSRID());
        Long ogfId = FeatureProperties.longValue(match.properties(), "OGF_ID");
        lake.setOgfId(ogfId);
        if (lake.getSourceLakeId() == null && ogfId != null) {
            lake.setSourceLakeId(String.valueOf(ogfId));
        }
        String officialName = FeatureProperties.text(match.properties(), OFFICIAL_NAME_KEYS);
        lake.setOfficialName(officialName);
        lake.setMunicipality(FeatureProperties.text(match.properties(), "MUNICIPALITY", "UPPER_TIER", "GEOGRAPHIC_TOWNSHIP"));
        lake.setWaterbodyLid(FeatureProperties.text(match.properties(), "WATERBODY_LID", "WB_LID", "GEL_NAME_IDENT"));
        lake.setBoundary(GeometrySupport.asMultiPolygon(geometry, "lake identity boundary"));
        var envelope = geometry.getEnvelopeInternal();
        lake.setBboxMinLng(envelope.getMinX());
        lake.setBboxMinLat(envelope.getMinY());
        lake.setBboxMaxLng(envelope.getMaxX());
        lake.setBboxMaxLat(envelope.getMaxY());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ogfId", ogfId);
        metadata.put("officialName", officialName);
        metadata.put("waterbodyType", FeatureProperties.text(match.properties(), "WATERBODY_TYPE", "FEATURE_TYPE", "TYPE"));
        metadata.put("sourceRecordId", match.sourceRecordId());
        lake.setIdentityMetadata(metadata);
    }

    private boolean nameMatches(Lake lake, ParsedFeature feature) {
        String official = FeatureProperties.text(feature.properties(), OFFICIAL_NAME_KEYS);
        if (official == null) {
            return false;
        }
        String lakeName = normalizeName(lake.getName());
        String featureName = normalizeName(official);
        return !lakeName.isBlank() && (featureName.equals(lakeName)
                || featureName.contains(lakeName)
                || lakeName.contains(featureName));
    }

    static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replace("lake", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isLakeType(String type) {
        if (type == null || type.isBlank()) {
            return true;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        return normalized.contains("lake") || normalized.contains("pond") || normalized.contains("reservoir");
    }

    private boolean isExcludedWatercourse(String type) {
        if (type == null) {
            return false;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        return normalized.contains("river") || normalized.contains("stream") || normalized.contains("creek");
    }

    private Long parseOgfId(String sourceLakeId) {
        if (sourceLakeId == null || sourceLakeId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(sourceLakeId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record Scored(ParsedFeature feature, double distanceKm, boolean contains, boolean nameMatch) {
    }
}
