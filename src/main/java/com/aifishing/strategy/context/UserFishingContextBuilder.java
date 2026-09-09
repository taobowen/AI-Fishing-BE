package com.aifishing.strategy.context;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.repo.BoatRepository;
import com.aifishing.common.enums.GearType;
import com.aifishing.fishingprofile.domain.FishingProfile;
import com.aifishing.fishingprofile.repo.FishingProfileRepository;
import com.aifishing.gear.repo.GearRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserFishingContextBuilder {

    private final FishingProfileRepository fishingProfileRepository;
    private final GearRepository gearRepository;
    private final BoatRepository boatRepository;

    public UserFishingContextBuilder(
            FishingProfileRepository fishingProfileRepository,
            GearRepository gearRepository,
            BoatRepository boatRepository
    ) {
        this.fishingProfileRepository = fishingProfileRepository;
        this.gearRepository = gearRepository;
        this.boatRepository = boatRepository;
    }

    public UserFishingContext build(UUID userId, UUID boatId) {
        FishingProfile profile = fishingProfileRepository.findByUserId(userId).orElse(null);
        var gear = gearRepository.findByUserIdOrderByNameAsc(userId).stream()
                .filter(item -> item.isActive())
                .filter(item -> item.getType() != GearType.LURE)
                .map(item -> new UserFishingContext.GearSummary(
                        item.getType(),
                        item.getName(),
                        item.getBrand(),
                        item.getMetadata()
                ))
                .toList();
        UserFishingContext.BoatSummary boat = null;
        if (boatId != null) {
            Boat entity = boatRepository.findById(boatId).orElse(null);
            if (entity != null && entity.isActive()) {
                boat = new UserFishingContext.BoatSummary(
                        entity.getName(),
                        entity.getType(),
                        entity.getPropulsionTypes(),
                        entity.getPrimaryTransitPropulsionType(),
                        entity.getMotors() == null ? java.util.List.of() : entity.getMotors().stream()
                                .map(motor -> new UserFishingContext.MotorSummary(
                                        motor.propulsionType(),
                                        motor.manufacturer(),
                                        motor.model(),
                                        motor.horsepower() == null ? null : motor.horsepower().doubleValue(),
                                        motor.thrustLb() == null ? null : motor.thrustLb().doubleValue()
                                ))
                                .toList(),
                        entity.getMaxSpeedKmh() == null ? null : entity.getMaxSpeedKmh().doubleValue()
                );
            }
        }
        if (profile == null) {
            return new UserFishingContext(null, java.util.List.of(), java.util.List.of(), gear, boat);
        }
        return new UserFishingContext(
                profile.getExperienceLevel(),
                profile.getPreferredSpecies(),
                profile.getPreferredFishingStyles(),
                gear,
                boat
        );
    }
}
