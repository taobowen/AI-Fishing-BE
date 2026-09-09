package com.aifishing.fishingprofile.domain;

import com.aifishing.common.domain.AuditedEntity;
import com.aifishing.common.enums.ExperienceLevel;
import com.aifishing.common.enums.FishSpecies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "fishing_profiles")
public class FishingProfile extends AuditedEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level")
    private ExperienceLevel experienceLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_species", nullable = false, columnDefinition = "jsonb")
    private List<FishSpecies> preferredSpecies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_fishing_styles", nullable = false, columnDefinition = "jsonb")
    private List<String> preferredFishingStyles = new ArrayList<>();

    @Column(name = "home_address")
    private String homeAddress;

    @Column(name = "home_city")
    private String homeCity;

    @Column(name = "home_region")
    private String homeRegion;

    @Column(name = "home_country")
    private String homeCountry;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "home_location", columnDefinition = "geometry(Point,4326)")
    private Point homeLocation;

    @Override
    public UUID id() {
        return id;
    }

    @Override
    protected void assignId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ExperienceLevel getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(ExperienceLevel experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public List<FishSpecies> getPreferredSpecies() {
        return preferredSpecies;
    }

    public void setPreferredSpecies(List<FishSpecies> preferredSpecies) {
        this.preferredSpecies = preferredSpecies == null ? new ArrayList<>() : preferredSpecies;
    }

    public List<String> getPreferredFishingStyles() {
        return preferredFishingStyles;
    }

    public void setPreferredFishingStyles(List<String> preferredFishingStyles) {
        this.preferredFishingStyles = preferredFishingStyles == null ? new ArrayList<>() : preferredFishingStyles;
    }

    public String getHomeAddress() {
        return homeAddress;
    }

    public void setHomeAddress(String homeAddress) {
        this.homeAddress = homeAddress;
    }

    public String getHomeCity() {
        return homeCity;
    }

    public void setHomeCity(String homeCity) {
        this.homeCity = homeCity;
    }

    public String getHomeRegion() {
        return homeRegion;
    }

    public void setHomeRegion(String homeRegion) {
        this.homeRegion = homeRegion;
    }

    public String getHomeCountry() {
        return homeCountry;
    }

    public void setHomeCountry(String homeCountry) {
        this.homeCountry = homeCountry;
    }

    public Point getHomeLocation() {
        return homeLocation;
    }

    public void setHomeLocation(Point homeLocation) {
        this.homeLocation = homeLocation;
    }
}
