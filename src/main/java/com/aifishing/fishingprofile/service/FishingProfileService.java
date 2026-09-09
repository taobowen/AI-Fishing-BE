package com.aifishing.fishingprofile.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.fishingprofile.api.FishingProfileResponse;
import com.aifishing.fishingprofile.api.UpsertFishingProfileRequest;
import com.aifishing.fishingprofile.domain.FishingProfile;
import com.aifishing.fishingprofile.repo.FishingProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
public class FishingProfileService {

    private final FishingProfileRepository fishingProfileRepository;
    private final CurrentUser currentUser;
    private final GeoMapper geoMapper;

    public FishingProfileService(
            FishingProfileRepository fishingProfileRepository,
            CurrentUser currentUser,
            GeoMapper geoMapper
    ) {
        this.fishingProfileRepository = fishingProfileRepository;
        this.currentUser = currentUser;
        this.geoMapper = geoMapper;
    }

    @Transactional(readOnly = true)
    public FishingProfileResponse getCurrent() {
        return toResponse(fishingProfileRepository.findByUserId(currentUser.id())
                .orElseThrow(() -> new NotFoundException("Fishing profile not found")));
    }

    @Transactional
    public FishingProfileResponse upsert(UpsertFishingProfileRequest request) {
        FishingProfile profile = fishingProfileRepository.findByUserId(currentUser.id())
                .orElseGet(() -> {
                    FishingProfile created = new FishingProfile();
                    created.setUserId(currentUser.id());
                    return created;
                });
        profile.setExperienceLevel(request.experienceLevel());
        profile.setPreferredSpecies(request.preferredSpecies() == null ? new ArrayList<>() : request.preferredSpecies());
        profile.setPreferredFishingStyles(request.preferredFishingStyles() == null ? new ArrayList<>() : request.preferredFishingStyles());
        profile.setHomeAddress(request.homeAddress());
        profile.setHomeCity(request.homeCity());
        profile.setHomeRegion(request.homeRegion());
        profile.setHomeCountry(request.homeCountry());
        profile.setHomeLocation(geoMapper.toPoint(request.homeLocation()));
        return toResponse(fishingProfileRepository.save(profile));
    }

    private FishingProfileResponse toResponse(FishingProfile profile) {
        return new FishingProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getExperienceLevel(),
                profile.getPreferredSpecies(),
                profile.getPreferredFishingStyles(),
                profile.getHomeAddress(),
                profile.getHomeCity(),
                profile.getHomeRegion(),
                profile.getHomeCountry(),
                geoMapper.toDto(profile.getHomeLocation()),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
