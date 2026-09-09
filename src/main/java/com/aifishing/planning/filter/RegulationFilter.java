package com.aifishing.planning.filter;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.lake.ingestion.domain.FishingRestriction;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.service.PlanningContext;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Component
public class RegulationFilter implements CandidateFilter {

    private static final Set<String> WHOLE_AREA_TYPES = Set.of("NO_FISHING", "SANCTUARY", "CLOSED");

    @Override
    public FilterResult apply(CandidateSpot candidate, PlanningContext context) {
        Point point = candidate.getLocation();
        if (point == null) {
            return FilterResult.reject(RejectionReason.INVALID_LOCATION);
        }
        LocalDate date = context.tripDate();
        for (FishingRestriction restriction : context.restrictions()) {
            if (!appliesOnDate(restriction, date) || !hasAuthoritativeGeometry(restriction)) {
                continue;
            }
            if (!restriction.getGeometry().covers(point)) {
                continue;
            }
            String type = normalize(restriction.getRestrictionType());
            if (!WHOLE_AREA_TYPES.contains(type)) {
                continue;
            }
            FishSpecies species = restriction.getSpecies();
            if (species == null) {
                return FilterResult.reject(RejectionReason.REGULATION_WHOLE_AREA);
            }
            if (species == context.primarySpecies()) {
                return FilterResult.reject(RejectionReason.REGULATION_PRIMARY_SPECIES);
            }
            if (context.secondarySpecies().contains(species)) {
                candidate.setSecondaryTargetRestricted(true);
                candidate.addWarning("secondaryTargetRestricted:" + species.name());
                return FilterResult.accept("secondaryTargetRestricted");
            }
        }
        return FilterResult.accept();
    }

    static boolean hasAuthoritativeGeometry(FishingRestriction restriction) {
        return restriction.getGeometry() != null && !restriction.getGeometry().isEmpty();
    }

    static boolean appliesOnDate(FishingRestriction restriction, LocalDate date) {
        if (date == null) {
            return false;
        }
        if (restriction.getValidFrom() != null && date.isBefore(restriction.getValidFrom())) {
            return false;
        }
        if (restriction.getValidTo() != null && date.isAfter(restriction.getValidTo())) {
            return false;
        }
        return true;
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
