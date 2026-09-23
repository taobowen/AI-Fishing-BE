package com.aifishing.trip.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.repo.BoatRepository;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripStatus;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.lake.service.LakeCardImageResolver;
import com.aifishing.launch.TripLaunchSelectionService;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.trip.api.CreateTripRequest;
import com.aifishing.trip.api.TripResponse;
import com.aifishing.trip.api.UpdateTripRequest;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final LakeRepository lakeRepository;
    private final BoatRepository boatRepository;
    private final CurrentUser currentUser;
    private final TripLaunchSelectionService launchSelectionService;
    private final LakeCardImageResolver cardImageResolver;

    public TripService(
            TripRepository tripRepository,
            LakeRepository lakeRepository,
            BoatRepository boatRepository,
            CurrentUser currentUser,
            TripLaunchSelectionService launchSelectionService,
            LakeCardImageResolver cardImageResolver
    ) {
        this.tripRepository = tripRepository;
        this.lakeRepository = lakeRepository;
        this.boatRepository = boatRepository;
        this.currentUser = currentUser;
        this.launchSelectionService = launchSelectionService;
        this.cardImageResolver = cardImageResolver;
    }

    @Transactional(readOnly = true)
    public List<TripResponse> list(TripStatus status, LocalDate from, LocalDate to) {
        List<Trip> trips = tripRepository.findOwned(currentUser.id(), status, from, to);
        if (trips.isEmpty()) {
            return List.of();
        }
        Map<UUID, Lake> lakes = lakeRepository.findAllById(
                trips.stream().map(Trip::getLakeId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Lake::getId, lake -> lake));
        Map<UUID, String> images = cardImageResolver.urlsFor(lakes.values());
        return trips.stream()
                .map(trip -> toResponse(trip, requireMappedLake(lakes, trip.getLakeId()), images.get(trip.getLakeId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TripResponse get(UUID id) {
        return toResponse(requireOwned(id));
    }

    @Transactional
    public TripResponse create(CreateTripRequest request) {
        Lake lake = requireLake(request.lakeId());
        TripClock.Window window = validateLakeLocalTimes(
                lake,
                request.plannedDate(),
                request.plannedEndDate(),
                request.fishingStartTime(),
                request.fishingEndTime()
        );
        UUID boatId = resolveBoatId(request.fishingMode(), request.boatId(), null);

        Trip trip = new Trip();
        trip.setUserId(currentUser.id());
        trip.setLakeId(lake.getId());
        trip.setPrimaryTargetSpecies(request.primaryTargetSpecies());
        trip.setSecondaryTargetSpecies(copySpecies(request.secondaryTargetSpecies()));
        trip.setPlannedDate(request.plannedDate());
        trip.setPlannedEndDate(window.plannedEndDate());
        trip.setFishingStartTime(request.fishingStartTime());
        trip.setFishingEndTime(request.fishingEndTime());
        trip.setBoatId(boatId);
        trip.setFishingMode(request.fishingMode());
        trip.setStatus(request.status() == null ? TripStatus.DRAFT : request.status());
        trip.setNotes(request.notes());
        Trip saved = tripRepository.save(trip);
        launchSelectionService.apply(saved.getId(), saved.getFishingMode(), lake, request.launchSelection());
        return toResponse(saved);
    }

    @Transactional
    public TripResponse update(UUID id, UpdateTripRequest request) {
        Trip trip = requireOwned(id);
        UUID previousLakeId = trip.getLakeId();
        FishingMode previousMode = trip.getFishingMode();
        UUID lakeId = request.lakeId() != null ? request.lakeId() : trip.getLakeId();
        Lake lake = requireLake(lakeId);
        LocalDate plannedDate = request.plannedDate() != null ? request.plannedDate() : trip.getPlannedDate();
        LocalDate plannedEndDate = request.plannedEndDate() != null ? request.plannedEndDate() : null;
        LocalTime start = request.fishingStartTime() != null ? request.fishingStartTime() : trip.getFishingStartTime();
        LocalTime end = request.fishingEndTime() != null ? request.fishingEndTime() : trip.getFishingEndTime();
        FishingMode mode = request.fishingMode() != null ? request.fishingMode() : trip.getFishingMode();

        TripClock.Window window = validateLakeLocalTimes(lake, plannedDate, plannedEndDate, start, end);
        UUID boatId = resolveBoatId(mode, request.boatId(), trip.getBoatId());

        trip.setLakeId(lake.getId());
        if (request.primaryTargetSpecies() != null) {
            trip.setPrimaryTargetSpecies(request.primaryTargetSpecies());
        }
        if (request.secondaryTargetSpecies() != null) {
            trip.setSecondaryTargetSpecies(copySpecies(request.secondaryTargetSpecies()));
        }
        trip.setPlannedDate(plannedDate);
        trip.setPlannedEndDate(window.plannedEndDate());
        trip.setFishingStartTime(start);
        trip.setFishingEndTime(end);
        trip.setBoatId(boatId);
        trip.setFishingMode(mode);
        if (request.status() != null) {
            trip.setStatus(request.status());
        }
        if (request.notes() != null) {
            trip.setNotes(request.notes());
        }
        Trip saved = tripRepository.save(trip);
        if (request.launchSelection() != null) {
            launchSelectionService.apply(saved.getId(), saved.getFishingMode(), lake, request.launchSelection());
        } else if (mode == FishingMode.SHORE) {
            launchSelectionService.apply(saved.getId(), mode, lake, null);
        } else if (!lakeId.equals(previousLakeId) || mode != previousMode) {
            launchSelectionService.revalidateForLake(saved.getId(), saved.getFishingMode(), lake);
        }
        return toResponse(saved);
    }

    private Trip requireOwned(UUID id) {
        return tripRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Trip not found"));
    }

    private Lake requireLake(UUID lakeId) {
        return lakeRepository.findById(lakeId)
                .orElseThrow(() -> new BadRequestException("Lake not found"));
    }

    private TripClock.Window validateLakeLocalTimes(
            Lake lake,
            LocalDate plannedDate,
            LocalDate plannedEndDate,
            LocalTime start,
            LocalTime end
    ) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(lake.getTimeZoneId());
        } catch (DateTimeException ex) {
            throw new BadRequestException("Lake has an invalid time zone");
        }
        TripClock.Window window = TripClock.resolve(plannedDate, plannedEndDate, start, end, zoneId);
        if (!window.endAfterStart()) {
            throw new BadRequestException("Resolved fishing end must be after fishing start in the lake local timezone");
        }
        if (!window.withinMaxDuration()) {
            throw new BadRequestException("Trip duration must be at most 24 hours");
        }
        return window;
    }

    private UUID resolveBoatId(FishingMode mode, UUID requestedBoatId, UUID existingBoatId) {
        if (mode == FishingMode.SHORE) {
            if (requestedBoatId != null) {
                throw new BadRequestException("boatId must be empty when fishingMode is SHORE");
            }
            return null;
        }
        UUID boatId = requestedBoatId != null ? requestedBoatId : existingBoatId;
        if (boatId == null) {
            throw new BadRequestException("boatId is required when fishingMode is BOAT");
        }
        Boat boat = boatRepository.findByIdAndUserIdAndActiveTrue(boatId, currentUser.id())
                .orElseThrow(() -> new BadRequestException("Boat not found"));
        return boat.getId();
    }

    private List<FishSpecies> copySpecies(List<FishSpecies> species) {
        return species == null ? new ArrayList<>() : new ArrayList<>(species);
    }

    private TripResponse toResponse(Trip trip) {
        Lake lake = lakeRepository.findById(trip.getLakeId())
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        return toResponse(trip, lake, cardImageResolver.urlFor(lake));
    }

    private TripResponse toResponse(Trip trip, Lake lake, String lakeCardImageUrl) {
        TripClock.Window window = TripClock.resolve(trip, lake);
        return new TripResponse(
                trip.getId(),
                trip.getUserId(),
                trip.getLakeId(),
                lakeCardImageUrl,
                lake.getTimeZoneId(),
                trip.getPrimaryTargetSpecies(),
                trip.getSecondaryTargetSpecies(),
                trip.getPlannedDate(),
                window.plannedEndDate(),
                trip.getFishingStartTime(),
                trip.getFishingEndTime(),
                window.startAt(),
                window.endAt(),
                trip.getBoatId(),
                trip.getFishingMode(),
                trip.getStatus(),
                trip.getNotes(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                launchSelectionService.toResponse(trip.getId(), trip.getFishingMode())
        );
    }

    private static Lake requireMappedLake(Map<UUID, Lake> lakes, UUID lakeId) {
        Lake lake = lakes.get(lakeId);
        if (lake == null) {
            throw new NotFoundException("Lake not found");
        }
        return lake;
    }
}
