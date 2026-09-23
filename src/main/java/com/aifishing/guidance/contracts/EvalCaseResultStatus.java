package com.aifishing.guidance.contracts;

/**
 * SKIP is not PASS. Coverage counts SKIP in the denominator only.
 * UNSCORABLE is not success or failure: a replayed action/target that differs
 * from the historically followed decision cannot use the historical outcome.
 */
public enum EvalCaseResultStatus {
    PASS,
    FAIL,
    ERROR,
    SKIP,
    UNSCORABLE
}
