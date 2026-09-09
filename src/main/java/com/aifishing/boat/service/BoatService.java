package com.aifishing.boat.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.boat.api.BoatResponse;
import com.aifishing.boat.api.CreateBoatRequest;
import com.aifishing.boat.api.ResolvedBoatCapabilityPreview;
import com.aifishing.boat.api.UpdateBoatRequest;
import com.aifishing.boat.api.WebBoatPreset;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.boat.capability.BoatCapabilityPriors;
import com.aifishing.boat.capability.BoatCapabilityResolver;
import com.aifishing.boat.capability.BoatFreeTextHasher;
import com.aifishing.boat.capability.ResolvedBoatCapability;
import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.repo.BoatRepository;
import com.aifishing.common.enums.BoatProvenance;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.CapabilitySource;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;
import com.aifishing.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BoatService {

    private static final Logger log = LoggerFactory.getLogger(BoatService.class);

    private final BoatRepository boatRepository;
    private final CurrentUser currentUser;
    private final BoatEquipmentValidator equipmentValidator;
    private final BoatCapabilityResolver capabilityResolver;

    public BoatService(
            BoatRepository boatRepository,
            CurrentUser currentUser,
            BoatEquipmentValidator equipmentValidator,
            BoatCapabilityResolver capabilityResolver
    ) {
        this.boatRepository = boatRepository;
        this.currentUser = currentUser;
        this.equipmentValidator = equipmentValidator;
        this.capabilityResolver = capabilityResolver;
    }

    @Transactional(readOnly = true)
    public List<BoatResponse> list() {
        return list(false);
    }

    @Transactional(readOnly = true)
    public List<BoatResponse> list(boolean includeSystem) {
        List<Boat> boats = includeSystem
                ? boatRepository.findByUserIdAndActiveTrueOrderByNameAsc(currentUser.id())
                : boatRepository.findByUserIdAndActiveTrueAndSystemGeneratedFalseOrderByNameAsc(currentUser.id());
        return boats.stream().map(boat -> BoatResponse.from(boat, null)).toList();
    }

    @Transactional
    public BoatResponse webDefault() {
        return webDefault(null);
    }

    @Transactional
    public BoatResponse webDefault(WebBoatPreset preset) {
        BoatProvenance provenance = provenanceFor(preset);
        return boatRepository.findByUserIdAndActiveTrueAndSystemGeneratedTrueAndProvenance(
                        currentUser.id(), provenance)
                .map(boat -> BoatResponse.from(boat, null))
                .orElseGet(() -> createWebDefault(preset, provenance));
    }

    private BoatResponse createWebDefault(WebBoatPreset preset, BoatProvenance provenance) {
        Boat boat = new Boat();
        boat.setUserId(currentUser.id());
        applyWebPreset(boat, preset);
        boat.setActive(true);
        boat.setSystemGenerated(true);
        boat.setProvenance(provenance);
        equipmentValidator.applyDefaultsAndValidate(boat);
        try {
            return BoatResponse.from(boatRepository.saveAndFlush(boat), null);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            return boatRepository.findByUserIdAndActiveTrueAndSystemGeneratedTrueAndProvenance(
                            currentUser.id(), provenance)
                    .map(existing -> BoatResponse.from(existing, null))
                    .orElseThrow(() -> ex);
        }
    }

    private static BoatProvenance provenanceFor(WebBoatPreset preset) {
        if (preset == null) {
            return BoatProvenance.WEB_DEFAULT;
        }
        return switch (preset) {
            case PADDLE -> BoatProvenance.WEB_DEFAULT_PADDLE;
            case MOTOR -> BoatProvenance.WEB_DEFAULT_MOTOR;
            case BASS -> BoatProvenance.WEB_DEFAULT_BASS;
        };
    }

    private static void applyWebPreset(Boat boat, WebBoatPreset preset) {
        if (preset == null) {
            boat.setName("Web default boat");
            boat.setType(BoatType.OTHER);
            boat.setPropulsionTypes(List.of(PropulsionType.NONE));
            boat.setPrimaryTransitPropulsionType(PropulsionType.NONE);
            return;
        }
        switch (preset) {
            case PADDLE -> {
                boat.setName("Web paddle craft");
                boat.setType(BoatType.KAYAK);
                boat.setPropulsionTypes(List.of(PropulsionType.PADDLE));
                boat.setPrimaryTransitPropulsionType(PropulsionType.PADDLE);
            }
            case MOTOR -> {
                boat.setName("Web motorboat");
                boat.setType(BoatType.INFLATABLE);
                boat.setPropulsionTypes(List.of(PropulsionType.ELECTRIC_TROLLING));
                boat.setPrimaryTransitPropulsionType(PropulsionType.ELECTRIC_TROLLING);
                boat.setMotors(List.of(new BoatMotor(
                        PropulsionType.ELECTRIC_TROLLING, null, null, null, new BigDecimal("55"))));
            }
            case BASS -> {
                boat.setName("Web bass boat");
                boat.setType(BoatType.FISHING_BOAT);
                boat.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD, PropulsionType.ELECTRIC_TROLLING));
                boat.setPrimaryTransitPropulsionType(PropulsionType.GAS_OUTBOARD);
                boat.setMotors(List.of(
                        new BoatMotor(PropulsionType.GAS_OUTBOARD, null, null, new BigDecimal("15"), null),
                        new BoatMotor(PropulsionType.ELECTRIC_TROLLING, null, null, null, new BigDecimal("55"))
                ));
            }
        }
    }

    @Transactional
    public BoatResponse get(UUID id) {
        return toResponse(requireOwned(id), false, null, false, false);
    }

    @Transactional
    public BoatResponse create(CreateBoatRequest request) {
        Boat boat = new Boat();
        boat.setUserId(currentUser.id());
        boat.setName(request.name().trim());
        boat.setType(request.type());
        boat.setManufacturer(request.manufacturer());
        boat.setModel(request.model());
        boat.setYear(request.year());
        boat.setPropulsionTypes(request.propulsionTypes());
        boat.setPrimaryTransitPropulsionType(request.primaryTransitPropulsionType());
        boat.setMotors(request.motors());
        boat.setConfigurationDescription(request.configurationDescription());
        boat.setMaxSpeedKmh(request.maxSpeedKmh());
        boat.setMeasuredCruiseSpeedKmh(request.measuredCruiseSpeedKmh());
        boat.setComfortableRoundTripRangeKm(request.comfortableRoundTripRangeKm());
        boat.setWindWaveOverride(request.windWaveOverride());
        boat.setNotes(request.notes());
        boat.setActive(true);
        boat.setSystemGenerated(false);
        boat.setProvenance(BoatProvenance.USER);
        boat.setFreeTextHash(BoatFreeTextHasher.hash(request.configurationDescription()));
        equipmentValidator.applyDefaultsAndValidate(boat);
        Boat saved = boatRepository.save(boat);
        return toResponse(saved, true, null, true, false);
    }

    @Transactional
    public BoatResponse update(UUID id, UpdateBoatRequest request) {
        Boat boat = requireOwned(id);
        if (request.name() != null) {
            boat.setName(request.name().trim());
        }
        if (request.type() != null) {
            boat.setType(request.type());
        }
        if (request.manufacturer() != null) {
            boat.setManufacturer(request.manufacturer());
        }
        if (request.model() != null) {
            boat.setModel(request.model());
        }
        if (request.year() != null) {
            boat.setYear(request.year());
        }
        if (request.propulsionTypes() != null) {
            boat.setPropulsionTypes(request.propulsionTypes());
        }
        if (request.primaryTransitPropulsionType() != null) {
            boat.setPrimaryTransitPropulsionType(request.primaryTransitPropulsionType());
        }
        if (request.motors() != null) {
            boat.setMotors(request.motors());
        }
        boolean textChanged = false;
        if (request.configurationDescription() != null) {
            String nextHash = BoatFreeTextHasher.hash(request.configurationDescription());
            textChanged = BoatFreeTextHasher.changed(boat.getFreeTextHash(), nextHash);
            boat.setConfigurationDescription(request.configurationDescription());
            boat.setFreeTextHash(nextHash);
        }
        if (request.maxSpeedKmh() != null) {
            boat.setMaxSpeedKmh(request.maxSpeedKmh());
        }
        if (request.measuredCruiseSpeedKmh() != null) {
            boat.setMeasuredCruiseSpeedKmh(request.measuredCruiseSpeedKmh());
        }
        if (request.comfortableRoundTripRangeKm() != null) {
            boat.setComfortableRoundTripRangeKm(request.comfortableRoundTripRangeKm());
        }
        if (request.windWaveOverride() != null) {
            boat.setWindWaveOverride(request.windWaveOverride());
        }
        if (request.notes() != null) {
            boat.setNotes(request.notes());
        }
        if (request.active() != null) {
            boat.setActive(request.active());
        }
        equipmentValidator.applyDefaultsAndValidate(boat);
        Boat saved = boatRepository.save(boat);
        if (textChanged) {
            BoatCapabilityPriors priors = BoatCapabilityPriors.from(saved);
            return toResponse(saved, true, priors, true, true);
        }
        return toResponse(saved, false, null, false, false);
    }

    @Transactional
    public BoatResponse resolveCapability(UUID id) {
        Boat boat = requireOwned(id);
        return toResponse(boat, true, BoatCapabilityPriors.from(boat), false, false);
    }

    @Transactional
    public void delete(UUID id) {
        Boat boat = requireOwned(id);
        boat.setActive(false);
        boatRepository.save(boat);
    }

    private Boat requireOwned(UUID id) {
        return boatRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Boat not found"));
    }

    private BoatResponse toResponse(
            Boat boat,
            boolean forceRefresh,
            BoatCapabilityPriors priors,
            boolean persistResolvedMetrics,
            boolean revising
    ) {
        ResolvedBoatCapabilityPreview preview = null;
        try {
            ResolvedBoatCapability resolved = capabilityResolver.resolve(boat, forceRefresh, priors);
            if (persistResolvedMetrics) {
                persistResolvedMetrics(boat, resolved, revising);
                equipmentValidator.applyDefaultsAndValidate(boat);
                boatRepository.save(boat);
            }
            preview = ResolvedBoatCapabilityPreview.from(resolved);
        } catch (Exception ex) {
            log.info("Boat capability preview unavailable for {}: {}", boat.getId(), ex.getMessage());
        }
        return BoatResponse.from(boat, preview);
    }

    private void persistResolvedMetrics(Boat boat, ResolvedBoatCapability resolved, boolean revising) {
        if (resolved == null) {
            return;
        }
        boolean fromAi = isAi(resolved.cruiseSpeedKmh() == null ? null : resolved.cruiseSpeedKmh().source())
                || isAi(resolved.estimatedPracticalRangeKm() == null ? null : resolved.estimatedPracticalRangeKm().source())
                || isAi(resolved.windWaveCapability() == null ? null : resolved.windWaveCapability().source());
        if (!revising && !fromAi) {
            return;
        }
        if (resolved.cruiseSpeedKmh() != null && resolved.cruiseSpeedKmh().value() != null) {
            boat.setMeasuredCruiseSpeedKmh(BigDecimal.valueOf(resolved.cruiseSpeedKmh().value()));
        }
        if (resolved.estimatedPracticalRangeKm() != null && resolved.estimatedPracticalRangeKm().value() != null) {
            boat.setComfortableRoundTripRangeKm(BigDecimal.valueOf(resolved.estimatedPracticalRangeKm().value()));
        }
        WindWaveCapability wind = resolved.windWaveCapability() == null ? null : resolved.windWaveCapability().value();
        if (wind != null) {
            boat.setWindWaveOverride(wind);
        }
    }

    private static boolean isAi(CapabilitySource source) {
        return source == CapabilitySource.AI_MODEL_ESTIMATED || source == CapabilitySource.AI_WEB_RESOLVED;
    }
}
