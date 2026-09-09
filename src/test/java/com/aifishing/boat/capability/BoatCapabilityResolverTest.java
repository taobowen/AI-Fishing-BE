package com.aifishing.boat.capability;

import com.aifishing.boat.capability.domain.BoatCapabilityProfileEntity;
import com.aifishing.boat.capability.repo.BoatCapabilityProfileRepository;
import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.CapabilitySource;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class BoatCapabilityResolverTest {

    private BoatCapabilityProperties properties;
    private FakeProfileRepository profileRepository;
    private RecordingReasoner reasoner;
    private BoatCapabilityResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new BoatCapabilityProperties();
        properties.setResolverVersion("v1");
        properties.setPromptVersion("p1");
        profileRepository = new FakeProfileRepository();
        reasoner = new RecordingReasoner();
        resolver = new BoatCapabilityResolver(
                properties,
                profileRepository,
                reasoner,
                new BoatCapabilityValidator(properties),
                new BoatCapabilityFallback(properties)
        );
    }

    @Test
    void userOverrideIsNotWrittenToSharedCache() {
        Boat boat = gasBoat(9.9);
        boat.setMeasuredCruiseSpeedKmh(BigDecimal.valueOf(17));
        reasoner.next = aiResult(20, 25.0, WindWaveCapability.MEDIUM, true);

        ResolvedBoatCapability resolved = resolver.resolve(boat);

        assertThat(resolved.cruiseSpeedKmh().value()).isEqualTo(17.0);
        assertThat(resolved.cruiseSpeedKmh().source()).isEqualTo(CapabilitySource.USER_OVERRIDE);
        assertThat(resolved.estimatedPracticalRangeKm().value()).isEqualTo(25.0);
        assertThat(resolved.estimatedPracticalRangeKm().source()).isEqualTo(CapabilitySource.AI_WEB_RESOLVED);
        BoatCapabilityProfileEntity saved = profileRepository.saved.getFirst();
        assertThat(saved.getCruiseSpeedSource()).isNotEqualTo(CapabilitySource.USER_OVERRIDE);
        assertThat(saved.getCruiseSpeedKmh()).isEqualByComparingTo("20.0");
        assertThat(saved.getRangeSource()).isNotEqualTo(CapabilitySource.USER_OVERRIDE);
    }

    @Test
    void maxSpeedIsNotAMeasuredOverride() {
        Boat boat = gasBoat(9.9);
        boat.setMaxSpeedKmh(BigDecimal.valueOf(42));
        boat.setMeasuredCruiseSpeedKmh(null);
        reasoner.fail = true;

        ResolvedBoatCapability resolved = resolver.resolve(boat);

        assertThat(resolved.cruiseSpeedKmh().source()).isNotEqualTo(CapabilitySource.USER_OVERRIDE);
        assertThat(boat.getMeasuredCruiseSpeedKmh()).isNull();
    }

    @Test
    void matchingCacheSkipsAiAndStaleTimerDoesNotForceRefresh() {
        Boat boat = gasBoat(9.9);
        BoatCapabilityProfileEntity cached = cachedRow(boat, "v1", "p1");
        cached.setUpdatedAt(Instant.now().minusSeconds(100L * 24 * 3600));
        cached.setCreatedAt(cached.getUpdatedAt());
        profileRepository.store.put(cached.getConfigurationFingerprint(), cached);

        ResolvedBoatCapability resolved = resolver.resolve(boat);

        assertThat(resolved.cruiseSpeedKmh().value()).isEqualTo(18.0);
        assertThat(reasoner.calls).isZero();
    }

    @Test
    void revisionWithPriorsDoesNotReusePriorLessCache() {
        Boat boat = gasBoat(9.9);
        boat.setConfigurationDescription("24V 100Ah LiFePO4");
        boat.setMeasuredCruiseSpeedKmh(BigDecimal.valueOf(12));
        BoatCapabilityProfileEntity cached = cachedRow(boat, "v1", "p1");
        profileRepository.store.put(cached.getConfigurationFingerprint(), cached);
        reasoner.next = aiResult(13, 28.0, WindWaveCapability.MEDIUM, false);

        ResolvedBoatCapability resolved = resolver.resolve(
                boat,
                true,
                new BoatCapabilityPriors(12.0, null, null)
        );

        assertThat(reasoner.calls).isEqualTo(1);
        assertThat(resolved.cruiseSpeedKmh().value()).isEqualTo(13.0);
        assertThat(resolved.cruiseSpeedKmh().source()).isNotEqualTo(CapabilitySource.USER_OVERRIDE);
    }

    @Test
    void versionBumpAndExplicitRefreshCallAi() {
        Boat boat = gasBoat(9.9);
        BoatCapabilityProfileEntity cached = cachedRow(boat, "old", "p1");
        profileRepository.store.put(cached.getConfigurationFingerprint(), cached);
        reasoner.next = aiResult(19, 30.0, WindWaveCapability.MEDIUM, false);

        ResolvedBoatCapability bumped = resolver.resolve(boat);
        assertThat(bumped.cruiseSpeedKmh().source()).isEqualTo(CapabilitySource.AI_MODEL_ESTIMATED);
        assertThat(reasoner.calls).isEqualTo(1);

        cached.setResolverVersion("v1");
        resolver.resolve(boat, true);
        assertThat(reasoner.calls).isEqualTo(2);
    }

    @Test
    void invalidAiRetriesThenFallsBackPerMetric() {
        Boat boat = inflatableElectric(55);
        reasoner.handler = (errors) -> {
            if (reasoner.calls == 1) {
                assertThat(errors).isEmpty();
            } else {
                assertThat(errors).isNotEmpty();
            }
            return aiResult(120, 8.0, WindWaveCapability.HIGH, false);
        };

        ResolvedBoatCapability resolved = resolver.resolve(boat);

        assertThat(reasoner.calls).isEqualTo(2);
        assertThat(resolved.cruiseSpeedKmh().source()).isEqualTo(CapabilitySource.CONSERVATIVE_FALLBACK);
        assertThat(resolved.cruiseSpeedKmh().value()).isLessThan(25);
    }

    @Test
    void nullableRangeDoesNotInventDistance() {
        Boat boat = inflatableElectric(55);
        reasoner.next = aiResult(6, null, WindWaveCapability.LOW, false);

        ResolvedBoatCapability resolved = resolver.resolve(boat);

        assertThat(resolved.estimatedPracticalRangeKm().value()).isNull();
        assertThat(resolved.warnings()).contains("BOAT_RANGE_LOW_CONFIDENCE");
    }

    @Test
    void electricThrustFallbackUsesConfiguredRangeWhenAiMissing() {
        BoatCapabilityProperties.NumericBand band = new BoatCapabilityProperties.NumericBand();
        band.setMaxInclusive(55);
        band.setPracticalRangeKm(8.0);
        band.setRangeConfidence(0.20);
        properties.getFallback().getElectricThrustBands().add(band);
        reasoner.fail = true;

        ResolvedBoatCapability resolved = resolver.resolve(inflatableElectric(55));

        assertThat(resolved.estimatedPracticalRangeKm().value()).isEqualTo(8.0);
        assertThat(resolved.estimatedPracticalRangeKm().source()).isEqualTo(CapabilitySource.CONSERVATIVE_FALLBACK);
    }

    private BoatCapabilityProfileEntity cachedRow(Boat boat, String resolverVersion, String promptVersion) {
        BoatConfigurationFingerprinter.Fingerprint fingerprint = BoatConfigurationFingerprinter.fingerprint(boat);
        BoatCapabilityProfileEntity entity = new BoatCapabilityProfileEntity();
        entity.setId(UUID.randomUUID());
        entity.setConfigurationFingerprint(fingerprint.hex());
        entity.setNormalizedConfiguration(fingerprint.normalizedConfiguration());
        entity.setCruiseSpeedKmh(BigDecimal.valueOf(18));
        entity.setCruiseSpeedConfidence(BigDecimal.valueOf(0.7));
        entity.setCruiseSpeedSource(CapabilitySource.AI_MODEL_ESTIMATED);
        entity.setPracticalRangeKm(BigDecimal.valueOf(30));
        entity.setRangeConfidence(BigDecimal.valueOf(0.7));
        entity.setRangeSource(CapabilitySource.AI_MODEL_ESTIMATED);
        entity.setWindWaveCapability(WindWaveCapability.MEDIUM);
        entity.setWindWaveConfidence(BigDecimal.valueOf(0.6));
        entity.setWindWaveSource(CapabilitySource.AI_MODEL_ESTIMATED);
        entity.setResolverVersion(resolverVersion);
        entity.setPromptVersion(promptVersion);
        entity.setResolvedAt(Instant.now());
        return entity;
    }

    private static BoatCapabilityReasoner.Result aiResult(
            double cruise,
            Double range,
            WindWaveCapability wind,
            boolean web
    ) {
        return new BoatCapabilityReasoner.Result(
                new AiBoatCapabilityEstimate(
                        new AiBoatCapabilityEstimate.MetricNumber(cruise, 0.8),
                        new AiBoatCapabilityEstimate.MetricNumber(range, range == null ? 0.2 : 0.8),
                        new AiBoatCapabilityEstimate.MetricEnum(wind, 0.7),
                        new AiBoatCapabilityEstimate.Reasoning("speed", "range", "wind"),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        web
                ),
                web,
                "gpt-test",
                Map.of(),
                Map.of()
        );
    }

    private static Boat gasBoat(double hp) {
        Boat boat = new Boat();
        boat.setType(BoatType.INFLATABLE);
        boat.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD));
        boat.setPrimaryTransitPropulsionType(PropulsionType.GAS_OUTBOARD);
        boat.setMotors(List.of(new BoatMotor(PropulsionType.GAS_OUTBOARD, null, null, BigDecimal.valueOf(hp), null)));
        return boat;
    }

    private static Boat inflatableElectric(double thrust) {
        Boat boat = new Boat();
        boat.setType(BoatType.INFLATABLE);
        boat.setPropulsionTypes(List.of(PropulsionType.ELECTRIC_TROLLING));
        boat.setPrimaryTransitPropulsionType(PropulsionType.ELECTRIC_TROLLING);
        boat.setMotors(List.of(new BoatMotor(PropulsionType.ELECTRIC_TROLLING, null, null, null, BigDecimal.valueOf(thrust))));
        return boat;
    }

    private static final class RecordingReasoner implements BoatCapabilityReasoner {
        private int calls;
        private boolean fail;
        private Result next;
        private java.util.function.Function<List<String>, Result> handler;

        @Override
        public Result estimate(Boat boat, List<String> previousValidationErrors) {
            calls++;
            if (fail) {
                throw new IllegalStateException("no openai");
            }
            if (handler != null) {
                return handler.apply(previousValidationErrors);
            }
            return next;
        }
    }

    private static final class FakeProfileRepository implements BoatCapabilityProfileRepository {
        private final Map<String, BoatCapabilityProfileEntity> store = new HashMap<>();
        private final List<BoatCapabilityProfileEntity> saved = new ArrayList<>();

        @Override
        public Optional<BoatCapabilityProfileEntity> findByConfigurationFingerprint(String configurationFingerprint) {
            return Optional.ofNullable(store.get(configurationFingerprint));
        }

        @Override
        public BoatCapabilityProfileEntity save(BoatCapabilityProfileEntity entity) {
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            store.put(entity.getConfigurationFingerprint(), entity);
            saved.add(entity);
            return entity;
        }

        @Override
        public <S extends BoatCapabilityProfileEntity> List<S> saveAll(Iterable<S> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BoatCapabilityProfileEntity> findById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BoatCapabilityProfileEntity> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BoatCapabilityProfileEntity> findAllById(Iterable<UUID> uuids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(BoatCapabilityProfileEntity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllById(Iterable<? extends UUID> uuids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll(Iterable<? extends BoatCapabilityProfileEntity> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BoatCapabilityProfileEntity> findAll(Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<BoatCapabilityProfileEntity> findAll(Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BoatCapabilityProfileEntity> S saveAndFlush(S entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BoatCapabilityProfileEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllInBatch(Iterable<BoatCapabilityProfileEntity> entities) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<UUID> uuids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAllInBatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BoatCapabilityProfileEntity getOne(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BoatCapabilityProfileEntity getById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BoatCapabilityProfileEntity getReferenceById(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BoatCapabilityProfileEntity> Optional<S> findOne(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BoatCapabilityProfileEntity> List<S> findAll(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BoatCapabilityProfileEntity> List<S> findAll(Example<S> example, Sort sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BoatCapabilityProfileEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BoatCapabilityProfileEntity> long count(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BoatCapabilityProfileEntity> boolean exists(Example<S> example) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends BoatCapabilityProfileEntity, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void flush() {
            throw new UnsupportedOperationException();
        }
    }
}
