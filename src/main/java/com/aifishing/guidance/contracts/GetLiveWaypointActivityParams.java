package com.aifishing.guidance.contracts;

import java.util.UUID;

public record GetLiveWaypointActivityParams(UUID tripWaypointId, Integer radiusMeters) {
}
