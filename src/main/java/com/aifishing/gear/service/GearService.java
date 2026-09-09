package com.aifishing.gear.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.enums.GearType;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.gear.api.CreateGearRequest;
import com.aifishing.gear.api.GearResponse;
import com.aifishing.gear.api.UpdateGearRequest;
import com.aifishing.gear.domain.Gear;
import com.aifishing.gear.lure.LureProfile;
import com.aifishing.gear.lure.LureProfileValidator;
import com.aifishing.gear.repo.GearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GearService {

    private final GearRepository gearRepository;
    private final CurrentUser currentUser;

    public GearService(GearRepository gearRepository, CurrentUser currentUser) {
        this.gearRepository = gearRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<GearResponse> list() {
        return gearRepository.findByUserIdOrderByNameAsc(currentUser.id()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GearResponse get(UUID id) {
        return toResponse(requireOwned(id));
    }

    @Transactional
    public GearResponse create(CreateGearRequest request) {
        LureProfile profile = resolveProfile(request.type(), request.lureProfile(), request.metadata());
        LureProfileValidator.validateIfPresent(request.type(), profile);
        String name = resolveName(request.name(), request.type(), profile, true);
        Gear gear = new Gear();
        gear.setUserId(currentUser.id());
        gear.setType(request.type());
        gear.setName(name);
        gear.setBrand(blankToNull(request.brand()));
        gear.setMetadata(LureProfile.mergeInto(request.metadata(), profile));
        gear.setActive(true);
        return toResponse(gearRepository.save(gear));
    }

    @Transactional
    public GearResponse update(UUID id, UpdateGearRequest request) {
        Gear gear = requireOwned(id);
        GearType type = request.type() != null ? request.type() : gear.getType();
        LureProfile existing = LureProfile.fromMetadata(gear.getMetadata());
        LureProfile incoming = request.lureProfile() != null
                ? request.lureProfile()
                : LureProfile.fromMetadata(request.metadata());
        LureProfile profile = incoming != null ? incoming : existing;
        if (type != GearType.LURE) {
            profile = null;
        }
        LureProfileValidator.validateIfPresent(type, profile);
        gear.setType(type);
        if (request.name() != null || (profile != null && blank(gear.getName()))) {
            gear.setName(resolveName(request.name() != null ? request.name() : gear.getName(), type, profile, false));
        }
        if (request.brand() != null) {
            gear.setBrand(blankToNull(request.brand()));
        }
        Map<String, Object> metadata = request.metadata() != null ? request.metadata() : gear.getMetadata();
        gear.setMetadata(LureProfile.mergeInto(metadata, profile));
        if (request.active() != null) {
            gear.setActive(request.active());
        }
        return toResponse(gearRepository.save(gear));
    }

    @Transactional
    public void delete(UUID id) {
        Gear gear = requireOwned(id);
        gear.setActive(false);
        gearRepository.save(gear);
    }

    private Gear requireOwned(UUID id) {
        return gearRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Gear not found"));
    }

    private GearResponse toResponse(Gear gear) {
        LureProfile profile = gear.getType() == GearType.LURE ? LureProfile.fromMetadata(gear.getMetadata()) : null;
        return new GearResponse(
                gear.getId(),
                gear.getUserId(),
                gear.getType(),
                gear.getName(),
                gear.getBrand(),
                gear.getMetadata(),
                gear.isActive(),
                gear.getCreatedAt(),
                gear.getUpdatedAt(),
                profile
        );
    }

    private static LureProfile resolveProfile(GearType type, LureProfile explicit, Map<String, Object> metadata) {
        if (type != GearType.LURE) {
            return null;
        }
        if (explicit != null) {
            return explicit;
        }
        return LureProfile.fromMetadata(metadata);
    }

    private static String resolveName(String name, GearType type, LureProfile profile, boolean creating) {
        String trimmed = name == null ? "" : name.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        if (type == GearType.LURE && profile != null) {
            String auto = profile.autoName();
            if (auto != null && !auto.isBlank()) {
                return auto;
            }
        }
        if (creating) {
            throw new BadRequestException("Name is required");
        }
        return trimmed;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.trim();
    }
}
