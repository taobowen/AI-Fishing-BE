package com.aifishing.strategy.domain;

public record DataLimitation(
        DataLimitationCode code,
        String message
) {
    public DataLimitation(DataLimitationCode code) {
        this(code, null);
    }
}
