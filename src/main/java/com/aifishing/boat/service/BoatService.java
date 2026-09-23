package com.aifishing.boat.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.boat.api.AnalyzeBoatRequest;
import com.aifishing.boat.api.AnalyzeBoatResponse;
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
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;
import com.aifishing.common.exception.BadRequestException;
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
        return toResponse(requireOwned(id), false, null);
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
        applyDefaultFlag(saved, request.isDefault() == null ? !hasUserDefault(saved.getId()) : request.isDefault());
        return toResponse(saved, false, null);
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
        if (request.configurationDescription() != null) {
            String nextHash = BoatFreeTextHasher.hash(request.configurationDescription());
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
        if (request.isDefault() != null) {
            applyDefaultFlag(saved, request.isDefault());
        } else if (!saved.isDefaultBoat() && !hasUserDefault(saved.getId())) {
            applyDefaultFlag(saved, true);
        }
        return toResponse(saved, false, null);
    }

    @Transactional
    public AnalyzeBoatResponse analyze(AnalyzeBoatRequest request) {
        String description = request.configurationDescription() == null ? "" : request.configurationDescription().trim();
        if (description.isBlank()) {
            throw new BadRequestException("configurationDescription is required");
        }
        Boat scratch = new Boat();
        scratch.setUserId(currentUser.id());
        scratch.setName("analyze");
        scratch.setConfigurationDescription(description);
        equipmentValidator.applyDefaultsAndValidate(scratch);
        BoatCapabilityPriors priors = new BoatCapabilityPriors(
                decimal(request.savedCruiseSpeedKmh()),
                decimal(request.savedPracticalRangeKm()),
                request.savedWindWaveCapability()
        );
        if (!priors.present()) {
            priors = null;
        }
        try {
            ResolvedBoatCapability resolved = capabilityResolver.resolve(scratch, true, priors);
            return AnalyzeBoatResponse.from(resolved);
        } catch (Exception ex) {
            log.info("Boat capability proposal unavailable: {}", ex.getMessage());
            return AnalyzeBoatResponse.from(null);
        }
    }

    @Transactional
    public BoatResponse resolveCapability(UUID id) {
        Boat boat = requireOwned(id);
        return toResponse(boat, true, BoatCapabilityPriors.from(boat));
    }

    @Transactional
    public void delete(UUID id) {
        Boat boat = requireOwned(id);
        boolean wasDefault = boat.isDefaultBoat();
        boat.setActive(false);
        boat.setDefaultBoat(false);
        boatRepository.save(boat);
        if (wasDefault) {
            promoteNextDefault(id);
        }
    }

    private boolean hasUserDefault(UUID exceptId) {
        return boatRepository.findByUserIdAndActiveTrueAndSystemGeneratedFalseOrderByNameAsc(currentUser.id())
                .stream()
                .anyMatch(boat -> boat.isDefaultBoat() && (exceptId == null || !exceptId.equals(boat.getId())));
    }

    private void applyDefaultFlag(Boat boat, boolean makeDefault) {
        if (boat.isSystemGenerated()) {
            boat.setDefaultBoat(false);
            boatRepository.save(boat);
            return;
        }
        if (!makeDefault) {
            if (boat.isDefaultBoat() && !hasUserDefault(boat.getId())) {
                boat.setDefaultBoat(true);
                boatRepository.save(boat);
                return;
            }
            boat.setDefaultBoat(false);
            boatRepository.save(boat);
            return;
        }
        for (Boat other : boatRepository.findByUserIdAndActiveTrueAndSystemGeneratedFalseOrderByNameAsc(boat.getUserId())) {
            if (!other.getId().equals(boat.getId()) && other.isDefaultBoat()) {
                other.setDefaultBoat(false);
                boatRepository.save(other);
            }
        }
        boatRepository.flush();
        boat.setDefaultBoat(true);
        boatRepository.save(boat);
    }

    private void promoteNextDefault(UUID exceptId) {
        boatRepository.findByUserIdAndActiveTrueAndSystemGeneratedFalseOrderByNameAsc(currentUser.id())
                .stream()
                .filter(boat -> !boat.getId().equals(exceptId))
                .findFirst()
                .ifPresent(next -> applyDefaultFlag(next, true));
    }

    private Boat requireOwned(UUID id) {
        return boatRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Boat not found"));
    }

    private BoatResponse toResponse(Boat boat, boolean forceRefresh, BoatCapabilityPriors priors) {
        ResolvedBoatCapabilityPreview preview = null;
        try {
            ResolvedBoatCapability resolved = capabilityResolver.resolve(boat, forceRefresh, priors);
            preview = ResolvedBoatCapabilityPreview.from(resolved);
        } catch (Exception ex) {
            log.info("Boat capability preview unavailable for {}: {}", boat.getId(), ex.getMessage());
        }
        return BoatResponse.from(boat, preview);
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
