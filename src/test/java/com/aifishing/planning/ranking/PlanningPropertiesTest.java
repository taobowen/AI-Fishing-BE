package com.aifishing.planning.ranking;

import com.aifishing.planning.PlanningProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlanningPropertiesTest {

    @Test
    void rankingWeightsSumToOne() {
        PlanningProperties.Ranking ranking = new PlanningProperties().getRanking();
        assertThat(ranking.sum()).isCloseTo(1.0, within(1e-9));
    }
}
