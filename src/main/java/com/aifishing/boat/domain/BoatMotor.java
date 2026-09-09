package com.aifishing.boat.domain;

import com.aifishing.common.enums.PropulsionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BoatMotor(
        PropulsionType propulsionType,
        String manufacturer,
        String model,
        BigDecimal horsepower,
        BigDecimal thrustLb
) {
}
