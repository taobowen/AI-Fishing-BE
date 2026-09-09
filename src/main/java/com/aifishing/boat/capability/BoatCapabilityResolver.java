package com.aifishing.boat.capability;

import com.aifishing.boat.capability.domain.BoatCapabilityProfileEntity;
import com.aifishing.boat.capability.repo.BoatCapabilityProfileRepository;
import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.CapabilitySource;
import com.aifishing.common.enums.WindWaveCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BoatCapabilityResolver {

    private static final Logger log = LoggerFactory.getLogger(BoatCapabilityResolver.class);

    private final BoatCapabilityProperties properties;
    private final BoatCapabilityProfileRepository profileRepository;
    private final BoatCapabilityReasoner reasoner;
    private final BoatCapabilityValidator validator;
    private final BoatCapabilityFallback fallback;

    public BoatCapabilityResolver(
            BoatCapabilityProperties properties,
            BoatCapabilityProfileRepository profileRepository,
            BoatCapabilityReasoner reasoner,
            BoatCapabilityValidator validator,
            BoatCapabilityFallback fallback
    ) {
        this.properties = properties;
        this.profileRepository = profileRepository;
        this.reasoner = reasoner;
        this.validator = validator;
        this.fallback = fallback;
    }

    @Transactional
    public ResolvedBoatCapability resolve(Boat boat) {
        return resolve(boat, false);
    }

    @Transactional
    public ResolvedBoatCapability resolve(Boat boat, boolean forceRefresh) {
        return resolve(boat, forceRefresh, null);
    }

    @Transactional
    public ResolvedBoatCapability resolve(Boat boat, boolean forceRefresh, BoatCapabilityPriors priors) {
        boolean revising = forceRefresh && priors != null && priors.present();
        BoatConfigurationFingerprinter.Fingerprint fingerprint = BoatConfigurationFingerprinter.fingerprint(
                boat,
                revising ? priors : null
        );
        Optional<BoatCapabilityProfileEntity> cached = profileRepository.findByConfigurationFingerprint(fingerprint.hex());
        boolean cacheFresh = cached.isPresent()
                && properties.getResolverVersion().equals(cached.get().getResolverVersion())
                && properties.getPromptVersion().equals(cached.get().getPromptVersion())
                && !forceRefresh
                && !revising;

        CapabilityMetric<Double> cruiseOverride = revising || boat.getMeasuredCruiseSpeedKmh() == null
                ? null
                : CapabilityMetric.of(boat.getMeasuredCruiseSpeedKmh().doubleValue(), 1.0, CapabilitySource.USER_OVERRIDE);
        CapabilityMetric<WindWaveCapability> windOverride = revising || boat.getWindWaveOverride() == null
                ? null
                : CapabilityMetric.of(boat.getWindWaveOverride(), 1.0, CapabilitySource.USER_OVERRIDE);

        boolean needSharedCruise = cruiseOverride == null && (!cacheFresh || cached.get().getCruiseSpeedKmh() == null);
        boolean needSharedRange = !cacheFresh || (cached.get().getPracticalRangeKm() == null && cached.get().getRangeConfidence() == null);
        boolean needSharedWind = !cacheFresh || cached.get().getWindWaveCapability() == null;

        BoatCapabilityProfileEntity shared = cacheFresh ? cached.get() : cached.orElseGet(BoatCapabilityProfileEntity::new);
        if (!cacheFresh && (needSharedCruise || needSharedRange || needSharedWind)) {
            shared = refreshShared(boat, fingerprint, shared, cached.orElse(null), forceRefresh, priors);
        }

        List<String> warnings = new ArrayList<>();
        if (shared.getWarnings() != null) {
            warnings.addAll(shared.getWarnings());
        }

        CapabilityMetric<Double> cruise = cruiseOverride != null
                ? cruiseOverride
                : metric(
                shared.getCruiseSpeedKmh(),
                shared.getCruiseSpeedConfidence(),
                shared.getCruiseSpeedSource(),
                weakLegacyOrFallback(boat)
        );
        CapabilityMetric<Double> range = metric(
                shared.getPracticalRangeKm(),
                shared.getRangeConfidence(),
                shared.getRangeSource(),
                fallback.range(boat)
        );
        CapabilityMetric<WindWaveCapability> wind = windOverride != null ? windOverride : windMetric(shared, boat);

        if (cruiseOverride == null && cruise.source() == CapabilitySource.WEAK_LEGACY_MAX_SPEED) {
            warnings.add("Cruise speed used a weak legacy max-speed hint.");
        }
        if (range.value() == null || !BoatCapabilityRangeMath.rangeReliable(range.confidence(), properties.getLowRangeConfidenceThreshold())) {
            warnings.add("BOAT_RANGE_LOW_CONFIDENCE");
        }

        return new ResolvedBoatCapability(
                cruise,
                range,
                wind,
                warnings.stream().distinct().toList(),
                stringList(shared.getRawResolutionMetadata(), "extractionGaps"),
                stringList(shared.getRawResolutionMetadata(), "missingHints"),
                shared.getId(),
                fingerprint.hex(),
                properties.getResolverVersion(),
                shared.getResolvedAt() == null ? Instant.now() : shared.getResolvedAt()
        );
    }

    private BoatCapabilityProfileEntity refreshShared(
            Boat boat,
            BoatConfigurationFingerprinter.Fingerprint fingerprint,
            BoatCapabilityProfileEntity target,
            BoatCapabilityProfileEntity previous,
            boolean forceRefresh,
            BoatCapabilityPriors priors
    ) {
        boolean reuse = previous != null
                && !forceRefresh
                && properties.getResolverVersion().equals(previous.getResolverVersion())
                && properties.getPromptVersion().equals(previous.getPromptVersion());
        CapabilityMetric<Double> cruise = reuse && previous.getCruiseSpeedKmh() != null
                ? metric(previous.getCruiseSpeedKmh(), previous.getCruiseSpeedConfidence(), previous.getCruiseSpeedSource(), fallback.cruise(boat))
                : null;
        CapabilityMetric<Double> range = reuse
                ? metric(previous.getPracticalRangeKm(), previous.getRangeConfidence(), previous.getRangeSource(), fallback.range(boat))
                : null;
        CapabilityMetric<WindWaveCapability> wind = reuse && previous.getWindWaveCapability() != null
                ? windMetric(previous, boat)
                : null;

        AiFill fill = tryAi(boat, priors);
        if (cruise == null) {
            cruise = firstValidCruise(boat, fill, fallback.cruise(boat));
        }
        if (range == null) {
            range = firstValidRange(boat, fill, fallback.range(boat));
        }
        if (wind == null) {
            wind = firstValidWind(boat, fill, fallback.wind(boat));
        }
        if (cruise == null) {
            cruise = fallback.cruise(boat);
        }

        target.setConfigurationFingerprint(fingerprint.hex());
        target.setNormalizedConfiguration(fingerprint.normalizedConfiguration());
        target.setCruiseSpeedKmh(decimal(cruise == null ? null : cruise.value()));
        target.setCruiseSpeedConfidence(decimal(cruise == null ? null : cruise.confidence()));
        target.setCruiseSpeedSource(sharedSource(cruise == null ? null : cruise.source()));
        target.setPracticalRangeKm(decimal(range == null ? null : range.value()));
        target.setRangeConfidence(decimal(range == null ? null : range.confidence()));
        target.setRangeSource(sharedSource(range == null ? null : range.source()));
        target.setWindWaveCapability(wind == null ? WindWaveCapability.LOW : wind.value());
        target.setWindWaveConfidence(decimal(wind == null ? 0.3 : wind.confidence()));
        target.setWindWaveSource(sharedSource(wind == null ? CapabilitySource.CONSERVATIVE_FALLBACK : wind.source()));
        target.setWarnings(fill.warnings);
        target.setResolverVersion(properties.getResolverVersion());
        target.setPromptVersion(properties.getPromptVersion());
        target.setModelId(fill.modelId);
        target.setWebSearchUsed(fill.webSearchUsed);
        target.setEvidenceMetadata(fill.evidence == null ? Map.of() : fill.evidence);
        Map<String, Object> raw = new java.util.LinkedHashMap<>(fill.raw == null ? Map.of() : fill.raw);
        raw.put("extractionGaps", fill.extractionGaps);
        raw.put("missingHints", fill.missingHints);
        if (fill.estimate != null) {
            raw.put("extractedType", fill.estimate.extractedType() == null ? null : fill.estimate.extractedType().name());
            raw.put("extractedPropulsionTypes", fill.estimate.extractedPropulsionTypes());
            raw.put("extractedMotors", fill.estimate.extractedMotors());
        }
        target.setRawResolutionMetadata(raw);
        target.setResolvedAt(Instant.now());
        applyExtractedEquipment(boat, fill.estimate);
        return profileRepository.save(target);
    }

    private AiFill tryAi(Boat boat, BoatCapabilityPriors priors) {
        List<String> errors = new ArrayList<>();
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                BoatCapabilityReasoner.Result result = reasoner.estimate(boat, errors, priors);
                AiBoatCapabilityEstimate estimate = result.estimate();
                List<String> cruiseErrors = validator.validateMetric(
                        "cruiseSpeedKmh",
                        estimate.cruiseSpeedKmh() == null ? null : estimate.cruiseSpeedKmh().value(),
                        estimate.cruiseSpeedKmh() == null ? null : estimate.cruiseSpeedKmh().confidence(),
                        true
                );
                cruiseErrors.addAll(validator.validateCombination(
                        boat,
                        estimate.cruiseSpeedKmh() == null ? null : estimate.cruiseSpeedKmh().value()
                ));
                List<String> rangeErrors = validator.validateMetric(
                        "practicalRangeKm",
                        estimate.practicalRangeKm() == null ? null : estimate.practicalRangeKm().value(),
                        estimate.practicalRangeKm() == null ? null : estimate.practicalRangeKm().confidence(),
                        false
                );
                List<String> windErrors = validator.validateWind(
                        estimate.windWaveCapability() == null ? null : estimate.windWaveCapability().value(),
                        estimate.windWaveCapability() == null ? null : estimate.windWaveCapability().confidence()
                );
                errors = new ArrayList<>();
                errors.addAll(cruiseErrors);
                errors.addAll(rangeErrors);
                errors.addAll(windErrors);
                if (!errors.isEmpty() && attempt == 0) {
                    continue;
                }
                CapabilitySource source = Boolean.TRUE.equals(estimate.webEvidenceUsed()) || result.webSearchUsed()
                        ? CapabilitySource.AI_WEB_RESOLVED
                        : CapabilitySource.AI_MODEL_ESTIMATED;
                List<String> warnings = estimate.warnings() == null ? List.of() : estimate.warnings();
                return new AiFill(
                        estimate,
                        result.webSearchUsed(),
                        result.modelId(),
                        result.evidenceMetadata(),
                        result.rawMetadata(),
                        warnings,
                        source,
                        estimate.extractionGaps(),
                        defaultHints(estimate)
                );
            } catch (Exception ex) {
                log.info("Boat capability AI unavailable: {}", ex.getMessage());
                return new AiFill(null, false, null, Map.of(), Map.of(), List.of());
            }
        }
        return new AiFill(null, false, null, Map.of(), Map.of(), List.of());
    }

    private CapabilityMetric<Double> firstValidCruise(Boat boat, AiFill fill, CapabilityMetric<Double> fallbackMetric) {
        if (fill.estimate != null && fill.estimate.cruiseSpeedKmh() != null) {
            List<String> errors = validator.validateMetric(
                    "cruiseSpeedKmh",
                    fill.estimate.cruiseSpeedKmh().value(),
                    fill.estimate.cruiseSpeedKmh().confidence(),
                    true
            );
            errors.addAll(validator.validateCombination(boat, fill.estimate.cruiseSpeedKmh().value()));
            if (errors.isEmpty() && fill.estimate.cruiseSpeedKmh().value() != null) {
                return CapabilityMetric.of(
                        fill.estimate.cruiseSpeedKmh().value(),
                        fill.estimate.cruiseSpeedKmh().confidence(),
                        fill.source
                );
            }
        }
        return fallbackMetric;
    }

    private CapabilityMetric<Double> firstValidRange(Boat boat, AiFill fill, CapabilityMetric<Double> fallbackMetric) {
        if (fill.estimate != null && fill.estimate.practicalRangeKm() != null) {
            List<String> errors = validator.validateMetric(
                    "practicalRangeKm",
                    fill.estimate.practicalRangeKm().value(),
                    fill.estimate.practicalRangeKm().confidence(),
                    false
            );
            if (errors.isEmpty()) {
                return CapabilityMetric.of(
                        fill.estimate.practicalRangeKm().value(),
                        fill.estimate.practicalRangeKm().confidence(),
                        fill.source
                );
            }
        }
        return fallbackMetric;
    }

    private CapabilityMetric<WindWaveCapability> firstValidWind(Boat boat, AiFill fill, CapabilityMetric<WindWaveCapability> fallbackMetric) {
        if (fill.estimate != null && fill.estimate.windWaveCapability() != null) {
            List<String> errors = validator.validateWind(
                    fill.estimate.windWaveCapability().value(),
                    fill.estimate.windWaveCapability().confidence()
            );
            if (errors.isEmpty()) {
                return CapabilityMetric.of(
                        fill.estimate.windWaveCapability().value(),
                        fill.estimate.windWaveCapability().confidence(),
                        fill.source
                );
            }
        }
        return fallbackMetric;
    }

    private CapabilityMetric<Double> weakLegacyOrFallback(Boat boat) {
        return weakLegacyOrFallback(boat, fallback.cruise(boat));
    }

    private CapabilityMetric<Double> weakLegacyOrFallback(Boat boat, CapabilityMetric<Double> fallbackMetric) {
        if (boat.getMaxSpeedKmh() != null && boat.getMaxSpeedKmh().doubleValue() > 0) {
            double hinted = Math.min(boat.getMaxSpeedKmh().doubleValue() * 0.65, properties.getMaxCruiseSpeedKmh());
            List<String> errors = validator.validateCombination(boat, hinted);
            if (errors.isEmpty()) {
                return CapabilityMetric.of(hinted, 0.25, CapabilitySource.WEAK_LEGACY_MAX_SPEED);
            }
        }
        return fallbackMetric;
    }

    private static CapabilityMetric<Double> metric(
            BigDecimal value,
            BigDecimal confidence,
            CapabilitySource source,
            CapabilityMetric<Double> fallbackMetric
    ) {
        if (source == CapabilitySource.USER_OVERRIDE) {
            return fallbackMetric;
        }
        if (value == null && (source == null || confidence == null)) {
            return fallbackMetric;
        }
        if (source == null) {
            return fallbackMetric;
        }
        return CapabilityMetric.of(
                value == null ? null : value.doubleValue(),
                confidence == null ? null : confidence.doubleValue(),
                source
        );
    }

    private CapabilityMetric<WindWaveCapability> windMetric(BoatCapabilityProfileEntity entity, Boat boat) {
        if (entity.getWindWaveCapability() == null || entity.getWindWaveSource() == CapabilitySource.USER_OVERRIDE) {
            return fallback.wind(boat);
        }
        return CapabilityMetric.of(
                entity.getWindWaveCapability(),
                entity.getWindWaveConfidence() == null ? null : entity.getWindWaveConfidence().doubleValue(),
                entity.getWindWaveSource()
        );
    }

    private static CapabilitySource sharedSource(CapabilitySource source) {
        if (source == null || source == CapabilitySource.USER_OVERRIDE) {
            return CapabilitySource.CONSERVATIVE_FALLBACK;
        }
        return source;
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static void applyExtractedEquipment(Boat boat, AiBoatCapabilityEstimate estimate) {
        if (boat == null || estimate == null) {
            return;
        }
        boolean missingType = boat.getType() == null || boat.getType() == com.aifishing.common.enums.BoatType.OTHER;
        if (missingType && estimate.extractedType() != null) {
            boat.setType(estimate.extractedType());
        }
        boolean missingPropulsion = boat.getPropulsionTypes() == null
                || boat.getPropulsionTypes().isEmpty()
                || (boat.getPropulsionTypes().size() == 1
                && boat.getPropulsionTypes().contains(com.aifishing.common.enums.PropulsionType.NONE));
        if (missingPropulsion && estimate.extractedPropulsionTypes() != null && !estimate.extractedPropulsionTypes().isEmpty()) {
            boat.setPropulsionTypes(estimate.extractedPropulsionTypes());
            if (boat.getPrimaryTransitPropulsionType() == null
                    || boat.getPrimaryTransitPropulsionType() == com.aifishing.common.enums.PropulsionType.NONE) {
                boat.setPrimaryTransitPropulsionType(estimate.extractedPropulsionTypes().getFirst());
            }
        }
        boolean missingMotors = boat.getMotors() == null || boat.getMotors().isEmpty();
        if (missingMotors && estimate.extractedMotors() != null && !estimate.extractedMotors().isEmpty()) {
            boat.setMotors(estimate.extractedMotors().stream()
                    .filter(motor -> motor != null && motor.propulsionType() != null)
                    .map(motor -> new com.aifishing.boat.domain.BoatMotor(
                            motor.propulsionType(),
                            motor.manufacturer(),
                            motor.model(),
                            motor.horsepower(),
                            motor.thrustLb()
                    ))
                    .toList());
        }
    }

    private List<String> defaultHints(AiBoatCapabilityEstimate estimate) {
        if (estimate != null && estimate.missingHints() != null && !estimate.missingHints().isEmpty()) {
            return estimate.missingHints();
        }
        List<String> hints = new ArrayList<>();
        boolean lowRange = estimate == null
                || estimate.practicalRangeKm() == null
                || estimate.practicalRangeKm().value() == null
                || estimate.practicalRangeKm().confidence() == null
                || estimate.practicalRangeKm().confidence() < properties.getLowRangeConfidenceThreshold();
        boolean lowCruise = estimate == null
                || estimate.cruiseSpeedKmh() == null
                || estimate.cruiseSpeedKmh().value() == null
                || estimate.cruiseSpeedKmh().confidence() == null
                || estimate.cruiseSpeedKmh().confidence() < properties.getLowRangeConfidenceThreshold();
        if (lowCruise) {
            hints.add("Add horsepower or electric thrust");
        }
        if (lowRange) {
            hints.add("Add battery Ah/voltage or fuel litres");
        }
        hints.add("Add hull size or whether you use it in open water");
        return hints;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Map<String, Object> raw, String key) {
        if (raw == null || raw.get(key) == null) {
            return List.of();
        }
        Object value = raw.get(key);
        if (value instanceof List<?> list) {
            return list.stream().filter(item -> item != null).map(Object::toString).toList();
        }
        return List.of();
    }

    private static final class AiFill {
        private final AiBoatCapabilityEstimate estimate;
        private final boolean webSearchUsed;
        private final String modelId;
        private final Map<String, Object> evidence;
        private final Map<String, Object> raw;
        private final List<String> warnings;
        private final CapabilitySource source;
        private final List<String> extractionGaps;
        private final List<String> missingHints;

        private AiFill(
                AiBoatCapabilityEstimate estimate,
                boolean webSearchUsed,
                String modelId,
                Map<String, Object> evidence,
                Map<String, Object> raw,
                List<String> warnings
        ) {
            this(estimate, webSearchUsed, modelId, evidence, raw, warnings, CapabilitySource.AI_MODEL_ESTIMATED, List.of(), List.of());
        }

        private AiFill(
                AiBoatCapabilityEstimate estimate,
                boolean webSearchUsed,
                String modelId,
                Map<String, Object> evidence,
                Map<String, Object> raw,
                List<String> warnings,
                CapabilitySource source,
                List<String> extractionGaps,
                List<String> missingHints
        ) {
            this.estimate = estimate;
            this.webSearchUsed = webSearchUsed;
            this.modelId = modelId;
            this.evidence = evidence;
            this.raw = raw;
            this.warnings = warnings == null ? List.of() : warnings;
            this.source = source == null ? CapabilitySource.AI_MODEL_ESTIMATED : source;
            this.extractionGaps = extractionGaps == null ? List.of() : extractionGaps;
            this.missingHints = missingHints == null ? List.of() : missingHints;
        }
    }
}
