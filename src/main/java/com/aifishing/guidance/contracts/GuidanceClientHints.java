package com.aifishing.guidance.contracts;

public record GuidanceClientHints(
        Double latitudeWgs84,
        Double longitudeWgs84,
        Double gpsAccuracyM,
        Double headingDegrees,
        Double speedMps
) {
}
