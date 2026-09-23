package com.aifishing.guidance.contracts;

public final class GuidanceSchemaVersion {

    /**
     * Canonical bundle id. Phase 2 Gaps 1–3 and Phase 3 activity/bite/routing
     * fields are additive in this same v1 bundle. Do not bump unless introducing
     * a breaking $defs change that cannot be expressed additively.
     */
    public static final String VALUE = "guidance.contracts.v1";
    public static final String BUNDLE_RESOURCE = "guidance/contracts/guidance-contracts.v1.json";
    public static final String BUNDLE_ID = "https://aifishing.local/guidance/contracts/guidance-contracts.v1.json";

    private GuidanceSchemaVersion() {
    }
}
