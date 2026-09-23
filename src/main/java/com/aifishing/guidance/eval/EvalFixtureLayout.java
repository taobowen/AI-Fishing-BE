package com.aifishing.guidance.eval;

/**
 * Classpath layout Workstream A owns. Platform fixtures run in default CI;
 * policy fixtures stay behind {@code -Pguidance-policy-eval}.
 */
public final class EvalFixtureLayout {

    public static final String FIXTURE_ROOT = "guidance/eval/fixtures";
    public static final String PLATFORM = FIXTURE_ROOT + "/platform";
    public static final String POLICY = FIXTURE_ROOT + "/policy";

    public static final String PLATFORM_TEST_PACKAGE = "com.aifishing.guidance.eval.platform";
    public static final String RELIABILITY_TEST_PACKAGE = "com.aifishing.guidance.eval.reliability";
    public static final String POLICY_TEST_PACKAGE = "com.aifishing.guidance.eval.policy";

    private EvalFixtureLayout() {
    }
}
