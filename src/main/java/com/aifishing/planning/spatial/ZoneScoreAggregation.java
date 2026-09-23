package com.aifishing.planning.spatial;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.ScoreBreakdown;
import com.aifishing.planning.ranking.SpotScore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zone and path visit scores come only from the ranked member targets that actually resolved.
 * A missed member lookup does not fall back to strategy weight.
 */
final class ZoneScoreAggregation {

    private ZoneScoreAggregation() {
    }

    static void index(Map<UUID, RankedCandidate> byId, RankedCandidate candidate) {
        if (candidate == null || candidate.spot() == null || candidate.score() == null) {
            return;
        }
        CandidateSpot spot = candidate.spot();
        put(byId, spot.getFishingTargetId(), candidate);
        if (spot.getTargetKind() != TargetKind.ZONE) {
            put(byId, spot.getFeatureId(), candidate);
            put(byId, spot.planningIdentity(), candidate);
        }
    }

    static RankedCandidate aggregate(CandidateSpot visit, Map<UUID, RankedCandidate> byId) {
        if (visit == null || visit.getZoneMembers() == null || visit.getZoneMembers().isEmpty()) {
            return null;
        }
        List<RankedCandidate> scored = new ArrayList<>();
        for (CandidateSpot member : visit.getZoneMembers()) {
            RankedCandidate match = resolve(byId, member);
            if (match == null || match.score() == null || !Double.isFinite(match.score().finalScore())) {
                continue;
            }
            scored.add(match);
        }
        if (scored.isEmpty()) {
            return null;
        }
        double score = scored.stream().mapToDouble(item -> item.score().finalScore()).average().orElseThrow();
        return new RankedCandidate(visit, new SpotScore(score, averageBreakdown(scored)), null);
    }

    private static RankedCandidate resolve(Map<UUID, RankedCandidate> byId, CandidateSpot member) {
        if (member == null || byId == null) {
            return null;
        }
        RankedCandidate match = get(byId, member.getFishingTargetId());
        if (match == null) {
            match = get(byId, member.getFeatureId());
        }
        if (match == null) {
            match = get(byId, member.planningIdentity());
        }
        return match;
    }

    private static void put(Map<UUID, RankedCandidate> byId, UUID id, RankedCandidate candidate) {
        if (id != null) {
            byId.putIfAbsent(id, candidate);
        }
    }

    private static RankedCandidate get(Map<UUID, RankedCandidate> byId, UUID id) {
        return id == null ? null : byId.get(id);
    }

    private static ScoreBreakdown averageBreakdown(List<RankedCandidate> scored) {
        List<ScoreBreakdown> parts = new ArrayList<>();
        for (RankedCandidate candidate : scored) {
            if (candidate.score().breakdown() != null) {
                parts.add(candidate.score().breakdown());
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        double mean = meanScore(scored);
        ScoreBreakdown strings = parts.get(0);
        double bestDistance = Double.POSITIVE_INFINITY;
        for (RankedCandidate candidate : scored) {
            ScoreBreakdown breakdown = candidate.score().breakdown();
            if (breakdown == null || breakdown.windOrientation() == null) {
                continue;
            }
            double distance = Math.abs(candidate.score().finalScore() - mean);
            if (distance < bestDistance) {
                bestDistance = distance;
                strings = breakdown;
            }
        }
        return new ScoreBreakdown(
                avg(parts, ScoreBreakdown::strategyMatch),
                avg(parts, ScoreBreakdown::depthMatch),
                avg(parts, ScoreBreakdown::featureConfidence),
                avg(parts, ScoreBreakdown::timeWindowMatch),
                avg(parts, ScoreBreakdown::gearCompatibility),
                avg(parts, ScoreBreakdown::weatherCompatibility),
                avg(parts, ScoreBreakdown::travelAccess),
                avgNullable(parts, ScoreBreakdown::historicalPerformance),
                avgNullable(parts, ScoreBreakdown::historicalEvidenceConfidence),
                avgNullable(parts, ScoreBreakdown::intrinsic),
                avgNullable(parts, ScoreBreakdown::strategyTimeEffect),
                avgNullable(parts, ScoreBreakdown::solarInfluenceStrength),
                avgNullable(parts, ScoreBreakdown::orientationExposure),
                avgNullable(parts, ScoreBreakdown::solarFishingEffect),
                strings.windOrientation(),
                avgNullable(parts, ScoreBreakdown::windFishingEffect),
                avgNullable(parts, ScoreBreakdown::temperatureEffect),
                avgNullable(parts, ScoreBreakdown::boatWeatherPenalty),
                avgNullable(parts, ScoreBreakdown::waitPenalty),
                avgNullable(parts, ScoreBreakdown::finalTimeAdjustedUtility)
        );
    }

    private static double meanScore(List<RankedCandidate> scored) {
        return scored.stream().mapToDouble(item -> item.score().finalScore()).average().orElse(0);
    }

    private static double avg(List<ScoreBreakdown> parts, java.util.function.ToDoubleFunction<ScoreBreakdown> fn) {
        return parts.stream().mapToDouble(fn).average().orElse(0);
    }

    private static Double avgNullable(List<ScoreBreakdown> parts, java.util.function.Function<ScoreBreakdown, Double> fn) {
        double sum = 0;
        int count = 0;
        for (ScoreBreakdown part : parts) {
            Double value = fn.apply(part);
            if (value != null && Double.isFinite(value)) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? null : sum / count;
    }
}
