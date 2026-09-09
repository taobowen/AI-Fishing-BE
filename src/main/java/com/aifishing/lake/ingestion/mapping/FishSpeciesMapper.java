package com.aifishing.lake.ingestion.mapping;

import com.aifishing.common.enums.FishSpecies;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class FishSpeciesMapper {

    public Optional<FishSpecies> map(String officialName) {
        if (officialName == null || officialName.isBlank()) {
            return Optional.empty();
        }
        String normalized = officialName.toLowerCase(Locale.ROOT)
                .replace(".", "")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return switch (normalized) {
            case "smallmouth bass", "small mouth bass", "micropterus dolomieu" -> Optional.of(FishSpecies.SMALLMOUTH_BASS);
            case "largemouth bass", "large mouth bass", "micropterus salmoides" -> Optional.of(FishSpecies.LARGEMOUTH_BASS);
            case "walleye", "yellow pickerel", "pickerel" -> Optional.of(FishSpecies.WALLEYE);
            case "northern pike", "pike" -> Optional.of(FishSpecies.NORTHERN_PIKE);
            case "muskellunge", "muskie", "musky" -> Optional.of(FishSpecies.MUSKELLUNGE);
            case "lake trout" -> Optional.of(FishSpecies.LAKE_TROUT);
            case "rainbow trout", "steelhead" -> Optional.of(FishSpecies.RAINBOW_TROUT);
            case "brook trout", "speckled trout" -> Optional.of(FishSpecies.BROOK_TROUT);
            case "yellow perch", "perch" -> Optional.of(FishSpecies.YELLOW_PERCH);
            case "crappie", "black crappie", "white crappie" -> Optional.of(FishSpecies.CRAPPIE);
            case "panfish", "bluegill", "pumpkinseed", "sunfish" -> Optional.of(FishSpecies.PANFISH);
            default -> Optional.empty();
        };
    }
}
