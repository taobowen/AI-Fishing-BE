package com.aifishing.planning.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchBudgetTest {

    @Test
    void stopExpandingWhenExhausted() {
        SearchBudget budget = new SearchBudget(3);
        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.tryConsume()).isTrue();
        assertThat(budget.tryConsume()).isFalse();
        assertThat(budget.reached()).isTrue();
        assertThat(budget.used()).isEqualTo(3);
        assertThat(budget.remaining()).isFalse();
        assertThat(budget.timeGuardHit()).isFalse();
    }

    @Test
    void wallClockGuardStopsExpansionWithoutChangingExpansionCap() {
        SearchBudget budget = new SearchBudget(1_000_000, 1);
        try {
            Thread.sleep(5);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        assertThat(budget.tryConsume()).isFalse();
        assertThat(budget.timeGuardHit()).isTrue();
        assertThat(budget.used()).isZero();
        assertThat(budget.maxExpansions()).isEqualTo(1_000_000);
    }
}
