package com.aifishing.guidance.contracts;

/**
 * Internal eval suite kind. {@link #SIMULATION} is reserved; implementation is deferred.
 */
public enum EvalSuiteKind {
    PLATFORM_REGRESSION,
    AGENT_POLICY_EVAL,
    SHADOW_REPLAY,
    ONLINE_ROLLUP,
    SIMULATION
}
