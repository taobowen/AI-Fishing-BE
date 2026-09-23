package com.aifishing.guidance.eval;

/**
 * JUnit tags for Maven surefire grouping. Default CI excludes {@link #POLICY}.
 */
public final class EvalSuiteTags {

    public static final String PLATFORM = "guidance-platform-eval";
    public static final String RELIABILITY = "guidance-reliability";
    public static final String POLICY = "guidance-policy-eval";

    private EvalSuiteTags() {
    }
}
