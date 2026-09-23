package com.aifishing.guidance.reliability;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.OnlineGuidanceMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hard release gate is delivered safety only:
 * {@code unsafeDeliveredRate == 0} and {@code invalidDeliveredWaypointRate == 0}.
 * Candidate rates are quality signals unless {@code candidateRatesBlocking} is on.
 * {@link EvalSuiteKind#PLATFORM_REGRESSION} always requires delivered rates at 0.
 */
@Component
public class ReliabilityGate {

    private final ReliabilityGatePolicy policy;

    @Autowired
    public ReliabilityGate(GuidanceProperties properties) {
        this(ReliabilityGatePolicy.from(properties));
    }

    ReliabilityGate(ReliabilityGatePolicy policy) {
        this.policy = policy == null ? ReliabilityGatePolicy.reportOnly() : policy;
    }

    public ReliabilityGateResult evaluate(SafetyRateCounts counts, EvalSuiteKind kind) {
        SafetyRateCounts safe = counts == null ? SafetyRateCounts.empty() : counts;
        return evaluate(safe.rates(), safe, kind);
    }

    public ReliabilityGateResult evaluate(OnlineGuidanceMetrics metrics, EvalSuiteKind kind) {
        return evaluate(SafetyRates.from(metrics), null, kind);
    }

    public ReliabilityGateResult evaluate(SafetyRates rates, EvalSuiteKind kind) {
        return evaluate(rates, null, kind);
    }

    public ReliabilityGateResult evaluate(SafetyRates rates, SafetyRateCounts counts, EvalSuiteKind kind) {
        SafetyRates safeRates = rates == null ? new SafetyRates(null, null, null, null, null) : rates;
        List<String> blocking = new ArrayList<>();
        List<String> signals = new ArrayList<>();

        if (!deliveredHardZero(safeRates.unsafeDeliveredRate(), counts == null ? 0 : counts.unsafeDeliveredCount())) {
            blocking.add("unsafeDeliveredRate must be 0");
        }
        if (!deliveredHardZero(
                safeRates.invalidDeliveredWaypointRate(),
                counts == null ? 0 : counts.invalidDeliveredWaypointCount()
        )) {
            blocking.add("invalidDeliveredWaypointRate must be 0");
        }

        recordCandidate(
                "candidateUnsafeRate",
                safeRates.candidateUnsafeRate(),
                policy.candidateUnsafeRateMax(),
                blocking,
                signals
        );
        recordCandidate(
                "candidateInvalidWaypointRate",
                safeRates.candidateInvalidWaypointRate(),
                policy.candidateInvalidWaypointRateMax(),
                blocking,
                signals
        );
        recordCandidate(
                "validatorInterceptionRate",
                safeRates.validatorInterceptionRate(),
                policy.validatorInterceptionRateMax(),
                blocking,
                signals
        );

        boolean hardPassed = blocking.stream().noneMatch(message ->
                message.startsWith("unsafeDeliveredRate") || message.startsWith("invalidDeliveredWaypointRate"));
        return new ReliabilityGateResult(blocking.isEmpty(), hardPassed, blocking, signals);
    }

    private void recordCandidate(
            String name,
            Double rate,
            double max,
            List<String> blocking,
            List<String> signals
    ) {
        if (rate == null || rate <= max) {
            return;
        }
        String message = name + " " + rate + " exceeds " + max;
        if (policy.candidateRatesBlocking()) {
            blocking.add(message);
        } else {
            signals.add(message);
        }
    }

    /**
     * Null rate means the delivered denominator is unknown (including zero deliveries).
     * That is not a delivered violation. A positive count or a non-zero rate fails the hard gate.
     */
    static boolean deliveredHardZero(Double rate, long deliveredViolationCount) {
        if (deliveredViolationCount > 0) {
            return false;
        }
        return rate == null || rate == 0.0d;
    }
}
