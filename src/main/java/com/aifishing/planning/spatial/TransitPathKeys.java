package com.aifishing.planning.spatial;

import java.util.UUID;

public final class TransitPathKeys {

    private TransitPathKeys() {
    }

    public static String launch(UUID accessPointId) {
        return accessPointId == null ? "launch" : "launch:" + accessPointId;
    }

    public static String visitEntry(UUID visitId) {
        return visitId == null ? "visit:unknown:entry" : "visit:" + visitId + ":entry";
    }

    public static String visitExit(UUID visitId) {
        return visitId == null ? "visit:unknown:exit" : "visit:" + visitId + ":exit";
    }
}
