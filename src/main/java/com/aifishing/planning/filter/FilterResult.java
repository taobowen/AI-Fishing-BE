package com.aifishing.planning.filter;

public record FilterResult(boolean accepted, RejectionReason reason, String warning) {

    public static FilterResult accept() {
        return new FilterResult(true, null, null);
    }

    public static FilterResult accept(String warning) {
        return new FilterResult(true, null, warning);
    }

    public static FilterResult reject(RejectionReason reason) {
        return new FilterResult(false, reason, null);
    }
}
