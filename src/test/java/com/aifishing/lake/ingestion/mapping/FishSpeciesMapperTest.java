package com.aifishing.lake.ingestion.mapping;

import com.aifishing.common.enums.FishSpecies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FishSpeciesMapperTest {

    private final FishSpeciesMapper mapper = new FishSpeciesMapper();

    @Test
    void mapsOfficialOntarioNames() {
        assertThat(mapper.map("Smallmouth Bass")).contains(FishSpecies.SMALLMOUTH_BASS);
        assertThat(mapper.map("Yellow Pickerel")).contains(FishSpecies.WALLEYE);
        assertThat(mapper.map("Muskie")).contains(FishSpecies.MUSKELLUNGE);
        assertThat(mapper.map("Brook Trout")).contains(FishSpecies.BROOK_TROUT);
    }

    @Test
    void unknownNamesAreNotDroppedSilently() {
        assertThat(mapper.map("Cisco")).isEmpty();
        assertThat(mapper.map("")).isEmpty();
        assertThat(mapper.map(null)).isEmpty();
    }
}
