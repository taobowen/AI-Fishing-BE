package com.aifishing.feedback.ranking;

public record EmpiricalEvidence(double historicalPerformance, double historicalEvidenceConfidence) {

    public static EmpiricalEvidence none() {
        return new EmpiricalEvidence(0.5, 0);
    }

    public boolean hasSignal() {
        return historicalEvidenceConfidence > 0;
    }
}
