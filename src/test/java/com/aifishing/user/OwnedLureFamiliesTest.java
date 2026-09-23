package com.aifishing.user;

import com.aifishing.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnedLureFamiliesTest {

    @Test
    void normalizesAndDedupesKnownIds() {
        assertThat(OwnedLureFamilies.normalize(List.of("jigs", "SOFT_PLASTICS", "JIGS")))
                .containsExactly("JIGS", "SOFT_PLASTICS");
    }

    @Test
    void rejectsCatalogItemThatIsNotAV2Chip() {
        assertThatThrownBy(() -> OwnedLureFamilies.normalize(List.of("NED_RIG")))
                .isInstanceOf(BadRequestException.class);
    }
}
