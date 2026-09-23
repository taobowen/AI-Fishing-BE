package com.aifishing.seed;

import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.domain.BoatMotor;
import com.aifishing.boat.repo.BoatRepository;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.ExperienceLevel;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.GearType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.TripStatus;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.fishingprofile.domain.FishingProfile;
import com.aifishing.fishingprofile.repo.FishingProfileRepository;
import com.aifishing.gear.domain.Gear;
import com.aifishing.gear.repo.GearRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import com.aifishing.user.domain.User;
import com.aifishing.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private final UserRepository userRepository;
    private final FishingProfileRepository fishingProfileRepository;
    private final GearRepository gearRepository;
    private final BoatRepository boatRepository;
    private final LakeRepository lakeRepository;
    private final TripRepository tripRepository;
    private final GeoMapper geoMapper;

    public DevDataInitializer(
            UserRepository userRepository,
            FishingProfileRepository fishingProfileRepository,
            GearRepository gearRepository,
            BoatRepository boatRepository,
            LakeRepository lakeRepository,
            TripRepository tripRepository,
            GeoMapper geoMapper
    ) {
        this.userRepository = userRepository;
        this.fishingProfileRepository = fishingProfileRepository;
        this.gearRepository = gearRepository;
        this.boatRepository = boatRepository;
        this.lakeRepository = lakeRepository;
        this.tripRepository = tripRepository;
        this.geoMapper = geoMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsById(DevSeedIds.USER_ID)) {
            ensureSecondDevUser();
            ensureReferenceLakes();
            ensureSeedBoatName();
            ensureSeedBoatEquipment();
            log.info("Dev seed data already present; ensured Ontario reference lakes");
            return;
        }

        User user = new User();
        user.setId(DevSeedIds.USER_ID);
        user.setEmail("dev@aifishing.local");
        user.setDisplayName("Dev Angler");
        userRepository.save(user);
        ensureSecondDevUser();

        FishingProfile profile = new FishingProfile();
        profile.setId(DevSeedIds.PROFILE_ID);
        profile.setUserId(user.getId());
        profile.setExperienceLevel(ExperienceLevel.INTERMEDIATE);
        profile.setPreferredSpecies(List.of(FishSpecies.SMALLMOUTH_BASS, FishSpecies.WALLEYE));
        profile.setPreferredFishingStyles(List.of("casting", "jigging"));
        fishingProfileRepository.save(profile);

        saveGear(DevSeedIds.GEAR_ROD_ID, user.getId(), GearType.ROD, "St. Croix Triumph", "St. Croix", Map.of("lengthFt", 7));
        saveGear(DevSeedIds.GEAR_REEL_ID, user.getId(), GearType.REEL, "Shimano Stradic", "Shimano", Map.of("size", 2500));
        saveGear(DevSeedIds.GEAR_LINE_ID, user.getId(), GearType.LINE, "PowerPro 20lb", "PowerPro", Map.of("lbTest", 20));
        saveGear(DevSeedIds.GEAR_LURE_ID, user.getId(), GearType.LURE, "Keitech Swing Impact", "Keitech", Map.of("color", "electric shad"));

        Boat boat = new Boat();
        boat.setId(DevSeedIds.BOAT_ID);
        boat.setUserId(user.getId());
        boat.setName("My skiff");
        boat.setType(BoatType.FISHING_BOAT);
        boat.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD, PropulsionType.ELECTRIC_TROLLING));
        boat.setPrimaryTransitPropulsionType(PropulsionType.GAS_OUTBOARD);
        boat.setMotors(List.of(
                new BoatMotor(PropulsionType.GAS_OUTBOARD, null, null, null, null),
                new BoatMotor(PropulsionType.ELECTRIC_TROLLING, null, null, null, null)
        ));
        boat.setMaxSpeedKmh(new BigDecimal("42.00"));
        boat.setMeasuredCruiseSpeedKmh(null);
        boat.setNotes("Seed boat for local development; boats are user-owned, not lake-specific");
        boat.setActive(true);
        boatRepository.save(boat);

        Lake lake = seedCatalogLake(ValidationCatalogService.VALIDATION_LAKES.getFirst());
        for (ValidationCatalogService.CatalogLake spec : ValidationCatalogService.VALIDATION_LAKES) {
            if (!spec.id().equals(DevSeedIds.LAKE_ID)) {
                seedCatalogLake(spec);
            }
        }

        Trip trip = new Trip();
        trip.setId(DevSeedIds.TRIP_ID);
        trip.setUserId(user.getId());
        trip.setLakeId(lake.getId());
        trip.setPrimaryTargetSpecies(FishSpecies.SMALLMOUTH_BASS);
        trip.setSecondaryTargetSpecies(List.of(FishSpecies.WALLEYE));
        trip.setPlannedDate(LocalDate.now().plusDays(7));
        trip.setFishingStartTime(LocalTime.of(6, 0));
        trip.setFishingEndTime(LocalTime.of(15, 0));
        trip.setBoatId(boat.getId());
        trip.setFishingMode(FishingMode.BOAT);
        trip.setStatus(TripStatus.DRAFT);
        trip.setNotes("Seed trip on Head Lake");
        tripRepository.save(trip);

        log.info("Loaded Phase 1/2 dev seed data. Default X-User-Id={}", DevSeedIds.USER_ID);
    }

    private void ensureSecondDevUser() {
        if (userRepository.existsById(DevSeedIds.OTHER_USER_ID)) {
            return;
        }
        User other = new User();
        other.setId(DevSeedIds.OTHER_USER_ID);
        other.setEmail("dev2@aifishing.local");
        other.setDisplayName("Dev Angler 2");
        userRepository.save(other);
    }

    private void ensureSeedBoatName() {
        boatRepository.findById(DevSeedIds.BOAT_ID).ifPresent(boat -> {
            if ("Head Lake Skiff".equals(boat.getName())) {
                boat.setName("My skiff");
                boat.setNotes("Seed boat for local development; boats are user-owned, not lake-specific");
                boatRepository.save(boat);
            }
        });
    }

    private void ensureSeedBoatEquipment() {
        boatRepository.findById(DevSeedIds.BOAT_ID).ifPresent(boat -> {
            boat.setPropulsionTypes(List.of(PropulsionType.GAS_OUTBOARD, PropulsionType.ELECTRIC_TROLLING));
            boat.setPrimaryTransitPropulsionType(PropulsionType.GAS_OUTBOARD);
            if (boat.getMotors() == null || boat.getMotors().isEmpty()) {
                boat.setMotors(List.of(
                        new BoatMotor(PropulsionType.GAS_OUTBOARD, null, null, null, null),
                        new BoatMotor(PropulsionType.ELECTRIC_TROLLING, null, null, null, null)
                ));
            }
            boatRepository.save(boat);
        });
    }

    private void ensureReferenceLakes() {
        for (ValidationCatalogService.CatalogLake spec : ValidationCatalogService.VALIDATION_LAKES) {
            lakeRepository.findById(spec.id()).ifPresentOrElse(existing -> {
                if (existing.getCardImagePath() == null || existing.getCardImagePath().isBlank()) {
                    existing.setCardImagePath(spec.cardImagePath());
                    lakeRepository.save(existing);
                }
            }, () -> seedCatalogLake(spec));
        }
    }

    private Lake seedCatalogLake(ValidationCatalogService.CatalogLake spec) {
        Lake lake = new Lake();
        lake.setId(spec.id());
        lake.setName(spec.name());
        lake.setProvince("Ontario");
        lake.setCountry("Canada");
        lake.setSource("MANUAL_SEED");
        lake.setCentroid(geoMapper.toPoint(new GeoPointDto(spec.lat(), spec.lng())));
        lake.setTimeZoneId("America/Toronto");
        lake.setCardImagePath(spec.cardImagePath());
        return lakeRepository.save(lake);
    }

    private void saveGear(java.util.UUID id, java.util.UUID userId, GearType type, String name, String brand, Map<String, Object> metadata) {
        Gear gear = new Gear();
        gear.setId(id);
        gear.setUserId(userId);
        gear.setType(type);
        gear.setName(name);
        gear.setBrand(brand);
        gear.setMetadata(metadata);
        gear.setActive(true);
        gearRepository.save(gear);
    }
}
