package com.aifishing.guidance.tools;

/**
 * Short {@code errorType} values for {@code ToolResultEnvelope}. Never a stack trace.
 */
public final class ToolErrorType {

    public static final String TIMEOUT = "TIMEOUT";
    public static final String INTERNAL = "INTERNAL";
    public static final String TOOL_NOT_REGISTERED = "TOOL_NOT_REGISTERED";
    public static final String CALL_BUDGET_EXCEEDED = "CALL_BUDGET_EXCEEDED";
    public static final String RESULT_TOO_LARGE = "RESULT_TOO_LARGE";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    private ToolErrorType() {
    }
}
