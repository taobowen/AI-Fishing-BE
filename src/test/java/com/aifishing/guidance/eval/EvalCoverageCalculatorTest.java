package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalCoverage;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalCoverageCalculatorTest {

    @Test
    void emptySuiteLeavesCoverageNullNotZero() {
        EvalCoverage coverage = EvalCoverageCalculator.fromStatuses(List.of());
        assertThat(coverage.loadedCases()).isZero();
        assertThat(coverage.skippedCases()).isZero();
        assertThat(coverage.attemptedCases()).isZero();
        assertThat(coverage.evalCoverage()).isNull();
        assertThat(EvalCoverage.empty().evalCoverage()).isNull();
        assertThat(EvalCoverageCalculator.meetsMinCoverage(coverage, 0.8)).isTrue();
    }

    @Test
    void skipCountsInDenominatorAndIsNotPass() {
        EvalCoverage coverage = EvalCoverageCalculator.fromStatuses(List.of(
                EvalCaseResultStatus.PASS,
                EvalCaseResultStatus.SKIP,
                EvalCaseResultStatus.FAIL,
                EvalCaseResultStatus.ERROR
        ));
        assertThat(coverage.loadedCases()).isEqualTo(3);
        assertThat(coverage.skippedCases()).isEqualTo(1);
        assertThat(coverage.attemptedCases()).isEqualTo(4);
        assertThat(coverage.passedCases()).isEqualTo(1);
        assertThat(coverage.evalCoverage()).isEqualTo(0.75);
    }

    @Test
    void massSkipFailsConfigurableMinCoverage() {
        List<EvalCaseResultStatus> statuses = Stream.concat(
                Stream.of(EvalCaseResultStatus.PASS),
                Stream.generate(() -> EvalCaseResultStatus.SKIP).limit(9)
        ).toList();
        EvalCoverage coverage = EvalCoverageCalculator.fromStatuses(statuses);
        assertThat(coverage.loadedCases()).isEqualTo(1);
        assertThat(coverage.skippedCases()).isEqualTo(9);
        assertThat(coverage.attemptedCases()).isEqualTo(10);
        assertThat(coverage.passedCases()).isEqualTo(1);
        assertThat(coverage.evalCoverage()).isEqualTo(0.1);
        assertThat(EvalCoverageCalculator.meetsMinCoverage(coverage, 0.8)).isFalse();
        assertThatThrownBy(() -> EvalCoverageCalculator.requireMinCoverage(coverage, 0.8))
                .isInstanceOf(EvalCiAssertionException.class)
                .hasMessageContaining("minCoverage")
                .hasMessageContaining("SKIP is not PASS");
    }

    @Test
    void constructorRecomputesCoverageAndRejectsPassedAsSkip() {
        EvalCoverage overwritten = new EvalCoverage(3, 1, 4, 2, 0.99);
        assertThat(overwritten.evalCoverage()).isEqualTo(0.75);
        assertThatThrownBy(() -> new EvalCoverage(1, 0, 1, 2, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passedCases");
        assertThat(EvalCoverageCalculator.fromStatuses(Collections.nCopies(10, EvalCaseResultStatus.SKIP))
                .passedCases()).isZero();
    }

    @Test
    void unscorableIsLoadedAndIsNotPassOrSkip() {
        EvalCoverage coverage = EvalCoverageCalculator.fromStatuses(List.of(
                EvalCaseResultStatus.PASS,
                EvalCaseResultStatus.UNSCORABLE,
                EvalCaseResultStatus.FAIL,
                EvalCaseResultStatus.SKIP
        ));
        assertThat(coverage.loadedCases()).isEqualTo(3);
        assertThat(coverage.skippedCases()).isEqualTo(1);
        assertThat(coverage.attemptedCases()).isEqualTo(4);
        assertThat(coverage.passedCases()).isEqualTo(1);
        assertThat(coverage.evalCoverage()).isEqualTo(0.75);
    }
}
