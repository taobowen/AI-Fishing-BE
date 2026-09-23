package com.aifishing.guidance;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.guidance")
public class GuidanceProperties {

    public enum RuntimeMode {
        OPENAI,
        DETERMINISTIC
    }

    private RuntimeMode runtimeMode;
    private long runTimeoutMs = 20_000;
    private long modelCallTimeoutMs = 12_000;
    private int maxModelTurns = 8;
    private int maxToolRounds = 4;
    private int maxToolCalls = 12;
    private int maxConcurrentToolCalls = 4;
    private int maxToolResultBytes = 65_536;
    private int maxContextTokens = 6_000;
    private String promptVersion = "guidance-prompt-v1";
    private String toolSchemaVersion = "guidance-tools-v1";
    private String contextVersion = "guidance-context-v1";
    private final Policy policy = Policy.seeded();
    private int weatherSnapshotMaxAgeMinutes = 15;
    private long heartbeatIntervalMs = 90_000;
    private boolean dispatchEnabled = true;
    private final Outbox outbox = new Outbox();
    private final LearningOutbox learningOutbox = new LearningOutbox();
    private final Reliability reliability = new Reliability();
    private final Memory memory = new Memory();
    private final Empirical empirical = new Empirical();
    private final Attribution attribution = new Attribution();
    private final Triggers triggers = new Triggers();
    private final Eval eval = new Eval();
    private final OpportunityRevisit opportunityRevisit = new OpportunityRevisit();

    public RuntimeMode getRuntimeMode() {
        return runtimeMode;
    }

    public void setRuntimeMode(RuntimeMode runtimeMode) {
        this.runtimeMode = runtimeMode;
    }

    public long getRunTimeoutMs() {
        return runTimeoutMs;
    }

    public void setRunTimeoutMs(long runTimeoutMs) {
        this.runTimeoutMs = runTimeoutMs;
    }

    public long getModelCallTimeoutMs() {
        return modelCallTimeoutMs;
    }

    public void setModelCallTimeoutMs(long modelCallTimeoutMs) {
        this.modelCallTimeoutMs = modelCallTimeoutMs;
    }

    public int getMaxModelTurns() {
        return maxModelTurns;
    }

    public void setMaxModelTurns(int maxModelTurns) {
        this.maxModelTurns = maxModelTurns;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaxConcurrentToolCalls() {
        return maxConcurrentToolCalls;
    }

    public void setMaxConcurrentToolCalls(int maxConcurrentToolCalls) {
        this.maxConcurrentToolCalls = maxConcurrentToolCalls;
    }

    public int getMaxToolResultBytes() {
        return maxToolResultBytes;
    }

    public void setMaxToolResultBytes(int maxToolResultBytes) {
        this.maxToolResultBytes = maxToolResultBytes;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getToolSchemaVersion() {
        return toolSchemaVersion;
    }

    public void setToolSchemaVersion(String toolSchemaVersion) {
        this.toolSchemaVersion = toolSchemaVersion;
    }

    public String getContextVersion() {
        return contextVersion;
    }

    public void setContextVersion(String contextVersion) {
        this.contextVersion = contextVersion;
    }

    public int getWeatherSnapshotMaxAgeMinutes() {
        return weatherSnapshotMaxAgeMinutes <= 0 ? 15 : weatherSnapshotMaxAgeMinutes;
    }

    public void setWeatherSnapshotMaxAgeMinutes(int weatherSnapshotMaxAgeMinutes) {
        this.weatherSnapshotMaxAgeMinutes = weatherSnapshotMaxAgeMinutes;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs <= 0 ? 90_000 : heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public boolean isDispatchEnabled() {
        return dispatchEnabled;
    }

    public void setDispatchEnabled(boolean dispatchEnabled) {
        this.dispatchEnabled = dispatchEnabled;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public LearningOutbox getLearningOutbox() {
        return learningOutbox;
    }

    public Reliability getReliability() {
        return reliability;
    }

    public Memory getMemory() {
        return memory;
    }

    public Empirical getEmpirical() {
        return empirical;
    }

    public Triggers getTriggers() {
        return triggers;
    }

    public Attribution getAttribution() {
        return attribution;
    }

    public Eval getEval() {
        return eval;
    }

    public OpportunityRevisit getOpportunityRevisit() {
        return opportunityRevisit;
    }

    public Policy getPolicy() {
        return policy;
    }

    /**
     * Available Agent policy versions. YAML is the catalog, not the live
     * production/candidate switch (Task C).
     */
    public static class Policy {
        private String defaultVersion = "v1";
        private Map<String, VersionSpec> versions = new LinkedHashMap<>();

        public static Policy seeded() {
            Policy policy = new Policy();
            policy.versions.put("v1", VersionSpec.currentProduction());
            policy.versions.put("v2", VersionSpec.currentProduction());
            return policy;
        }

        public String getDefaultVersion() {
            return defaultVersion == null || defaultVersion.isBlank() ? "v1" : defaultVersion;
        }

        public void setDefaultVersion(String defaultVersion) {
            this.defaultVersion = defaultVersion;
        }

        public Map<String, VersionSpec> getVersions() {
            return versions;
        }

        public void setVersions(Map<String, VersionSpec> versions) {
            this.versions = versions == null ? new LinkedHashMap<>() : versions;
        }

        public static class VersionSpec {
            private String promptProfile = "guidance-prompt-v1";
            private String contextProfile = "guidance-context-v1";
            private String toolsetProfile = "guidance-tools-v1";
            private String modelProfile = "current";
            private String learningProfile = "empirical-algorithm-1";

            public static VersionSpec currentProduction() {
                return new VersionSpec();
            }

            public String getPromptProfile() {
                return promptProfile;
            }

            public void setPromptProfile(String promptProfile) {
                this.promptProfile = promptProfile;
            }

            public String getContextProfile() {
                return contextProfile;
            }

            public void setContextProfile(String contextProfile) {
                this.contextProfile = contextProfile;
            }

            public String getToolsetProfile() {
                return toolsetProfile;
            }

            public void setToolsetProfile(String toolsetProfile) {
                this.toolsetProfile = toolsetProfile;
            }

            public String getModelProfile() {
                return modelProfile;
            }

            public void setModelProfile(String modelProfile) {
                this.modelProfile = modelProfile;
            }

            public String getLearningProfile() {
                return learningProfile;
            }

            public void setLearningProfile(String learningProfile) {
                this.learningProfile = learningProfile;
            }
        }
    }

    public static class Outbox {
        private long pollIntervalMs = 15_000;
        private long staleClaimMs = 120_000;

        public long getPollIntervalMs() {
            return pollIntervalMs <= 0 ? 15_000 : pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public long getStaleClaimMs() {
            return staleClaimMs <= 0 ? 120_000 : staleClaimMs;
        }

        public void setStaleClaimMs(long staleClaimMs) {
            this.staleClaimMs = staleClaimMs;
        }
    }

    public static class LearningOutbox {
        private boolean enabled = true;
        private long pollIntervalMs = 15_000;
        private long staleClaimMs = 120_000;
        private int maxAttempts = 8;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs <= 0 ? 15_000 : pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public long getStaleClaimMs() {
            return staleClaimMs <= 0 ? 120_000 : staleClaimMs;
        }

        public void setStaleClaimMs(long staleClaimMs) {
            this.staleClaimMs = staleClaimMs;
        }

        public int getMaxAttempts() {
            return maxAttempts <= 0 ? 8 : maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    /**
     * Candidate safety rates are quality signals. Delivered unsafe / invalid-waypoint
     * rates are the hard release gate and stay blocking.
     */
    public static class Reliability {
        private double candidateUnsafeRateMax = 0.0;
        private double candidateInvalidWaypointRateMax = 0.0;
        private double validatorInterceptionRateMax = 0.0;
        private boolean candidateRatesBlocking = false;

        public double getCandidateUnsafeRateMax() {
            return clampUnit(candidateUnsafeRateMax);
        }

        public void setCandidateUnsafeRateMax(double candidateUnsafeRateMax) {
            this.candidateUnsafeRateMax = candidateUnsafeRateMax;
        }

        public double getCandidateInvalidWaypointRateMax() {
            return clampUnit(candidateInvalidWaypointRateMax);
        }

        public void setCandidateInvalidWaypointRateMax(double candidateInvalidWaypointRateMax) {
            this.candidateInvalidWaypointRateMax = candidateInvalidWaypointRateMax;
        }

        public double getValidatorInterceptionRateMax() {
            return clampUnit(validatorInterceptionRateMax);
        }

        public void setValidatorInterceptionRateMax(double validatorInterceptionRateMax) {
            this.validatorInterceptionRateMax = validatorInterceptionRateMax;
        }

        public boolean isCandidateRatesBlocking() {
            return candidateRatesBlocking;
        }

        public void setCandidateRatesBlocking(boolean candidateRatesBlocking) {
            this.candidateRatesBlocking = candidateRatesBlocking;
        }

        private static double clampUnit(double value) {
            if (value < 0) {
                return 0.0;
            }
            if (value > 1) {
                return 1.0;
            }
            return value;
        }
    }

    public static class Memory {
        private double inferredConfidenceThreshold = 0.5;
        private int inferredRecencyHalfLifeDays = 45;
        private int recentHistoryLimit = 6;
        private int sessionSummaryLlmMinFacts = 8;
        private int sessionSummaryLlmMinChars = 800;

        public double getInferredConfidenceThreshold() {
            return inferredConfidenceThreshold <= 0 || inferredConfidenceThreshold > 1
                    ? 0.5
                    : inferredConfidenceThreshold;
        }

        public void setInferredConfidenceThreshold(double inferredConfidenceThreshold) {
            this.inferredConfidenceThreshold = inferredConfidenceThreshold;
        }

        public int getInferredRecencyHalfLifeDays() {
            return inferredRecencyHalfLifeDays <= 0 ? 45 : inferredRecencyHalfLifeDays;
        }

        public void setInferredRecencyHalfLifeDays(int inferredRecencyHalfLifeDays) {
            this.inferredRecencyHalfLifeDays = inferredRecencyHalfLifeDays;
        }

        public int getRecentHistoryLimit() {
            return recentHistoryLimit <= 0 ? 6 : recentHistoryLimit;
        }

        public void setRecentHistoryLimit(int recentHistoryLimit) {
            this.recentHistoryLimit = recentHistoryLimit;
        }

        public int getSessionSummaryLlmMinFacts() {
            return sessionSummaryLlmMinFacts <= 0 ? 8 : sessionSummaryLlmMinFacts;
        }

        public void setSessionSummaryLlmMinFacts(int sessionSummaryLlmMinFacts) {
            this.sessionSummaryLlmMinFacts = sessionSummaryLlmMinFacts;
        }

        public int getSessionSummaryLlmMinChars() {
            return sessionSummaryLlmMinChars <= 0 ? 800 : sessionSummaryLlmMinChars;
        }

        public void setSessionSummaryLlmMinChars(int sessionSummaryLlmMinChars) {
            this.sessionSummaryLlmMinChars = sessionSummaryLlmMinChars;
        }
    }

    public static class Empirical {
        private int minContributingSessionWaypoints = 3;
        private double minSampleConfidence = 0.25;
        private int minEffortMinutes = 15;
        private double priorEffortHours = 4;
        private double priorFishOnPerHour = 0.5;

        public int getMinContributingSessionWaypoints() {
            return minContributingSessionWaypoints <= 0 ? 3 : minContributingSessionWaypoints;
        }

        public void setMinContributingSessionWaypoints(int minContributingSessionWaypoints) {
            this.minContributingSessionWaypoints = minContributingSessionWaypoints;
        }

        public double getMinSampleConfidence() {
            return minSampleConfidence <= 0 || minSampleConfidence > 1 ? 0.25 : minSampleConfidence;
        }

        public void setMinSampleConfidence(double minSampleConfidence) {
            this.minSampleConfidence = minSampleConfidence;
        }

        public int getMinEffortMinutes() {
            return minEffortMinutes <= 0 ? 15 : minEffortMinutes;
        }

        public void setMinEffortMinutes(int minEffortMinutes) {
            this.minEffortMinutes = minEffortMinutes;
        }

        public double getPriorEffortHours() {
            return priorEffortHours <= 0 ? 4 : priorEffortHours;
        }

        public void setPriorEffortHours(double priorEffortHours) {
            this.priorEffortHours = priorEffortHours;
        }

        public double getPriorFishOnPerHour() {
            return priorFishOnPerHour <= 0 ? 0.5 : priorFishOnPerHour;
        }

        public void setPriorFishOnPerHour(double priorFishOnPerHour) {
            this.priorFishOnPerHour = priorFishOnPerHour;
        }

        public boolean insufficient(int contributingSessionWaypointCount, Double sampleConfidence) {
            return contributingSessionWaypointCount < getMinContributingSessionWaypoints()
                    || sampleConfidence == null
                    || sampleConfidence < getMinSampleConfidence();
        }
    }

    public static class Triggers {
        private int noBiteMinutes = 20;
        private int fishOnCooldownMinutes = 5;
        private int repeatedBiteCount = 3;
        private int repeatedBiteWindowMinutes = 10;

        public int getNoBiteMinutes() {
            return noBiteMinutes <= 0 ? 20 : noBiteMinutes;
        }

        public void setNoBiteMinutes(int noBiteMinutes) {
            this.noBiteMinutes = noBiteMinutes;
        }

        public int getFishOnCooldownMinutes() {
            return fishOnCooldownMinutes < 0 ? 0 : fishOnCooldownMinutes;
        }

        public void setFishOnCooldownMinutes(int fishOnCooldownMinutes) {
            this.fishOnCooldownMinutes = fishOnCooldownMinutes;
        }

        public int getRepeatedBiteCount() {
            return repeatedBiteCount <= 0 ? 3 : repeatedBiteCount;
        }

        public void setRepeatedBiteCount(int repeatedBiteCount) {
            this.repeatedBiteCount = repeatedBiteCount;
        }

        public int getRepeatedBiteWindowMinutes() {
            return repeatedBiteWindowMinutes <= 0 ? 10 : repeatedBiteWindowMinutes;
        }

        public void setRepeatedBiteWindowMinutes(int repeatedBiteWindowMinutes) {
            this.repeatedBiteWindowMinutes = repeatedBiteWindowMinutes;
        }
    }

    public static class Attribution {
        private int maxStayAttributionMinutes = 30;
        private int lureWindowMinutes = 15;
        private int depthWindowMinutes = 15;
        private int retrieveWindowMinutes = 10;
        private int moveWindowMinutes = 30;
        private int consecutiveFailureThreshold = 3;
        private int strategyChangeWindowMinutes = 60;

        public int getMaxStayAttributionMinutes() {
            return maxStayAttributionMinutes <= 0 ? 30 : maxStayAttributionMinutes;
        }

        public void setMaxStayAttributionMinutes(int maxStayAttributionMinutes) {
            this.maxStayAttributionMinutes = maxStayAttributionMinutes;
        }

        public int getLureWindowMinutes() {
            return lureWindowMinutes <= 0 ? 15 : lureWindowMinutes;
        }

        public void setLureWindowMinutes(int lureWindowMinutes) {
            this.lureWindowMinutes = lureWindowMinutes;
        }

        public int getDepthWindowMinutes() {
            return depthWindowMinutes <= 0 ? 15 : depthWindowMinutes;
        }

        public void setDepthWindowMinutes(int depthWindowMinutes) {
            this.depthWindowMinutes = depthWindowMinutes;
        }

        public int getRetrieveWindowMinutes() {
            return retrieveWindowMinutes <= 0 ? 10 : retrieveWindowMinutes;
        }

        public void setRetrieveWindowMinutes(int retrieveWindowMinutes) {
            this.retrieveWindowMinutes = retrieveWindowMinutes;
        }

        public int getMoveWindowMinutes() {
            return moveWindowMinutes <= 0 ? 30 : moveWindowMinutes;
        }

        public void setMoveWindowMinutes(int moveWindowMinutes) {
            this.moveWindowMinutes = moveWindowMinutes;
        }

        public int getConsecutiveFailureThreshold() {
            return consecutiveFailureThreshold <= 0 ? 3 : consecutiveFailureThreshold;
        }

        public void setConsecutiveFailureThreshold(int consecutiveFailureThreshold) {
            this.consecutiveFailureThreshold = consecutiveFailureThreshold;
        }

        public int getStrategyChangeWindowMinutes() {
            return strategyChangeWindowMinutes <= 0 ? 60 : strategyChangeWindowMinutes;
        }

        public void setStrategyChangeWindowMinutes(int strategyChangeWindowMinutes) {
            this.strategyChangeWindowMinutes = strategyChangeWindowMinutes;
        }
    }

    /**
     * Offline eval CI gates. Token budget is asserted only when usage telemetry exists.
     * Wall-clock latency is recorded, never a blocking threshold.
     */
    public static class Eval {
        private double minCoverage = 0.8;
        private Integer maxTotalTokens;
        private String pricingVersion;
        private Double inputUsdPer1kTokens;
        private Double outputUsdPer1kTokens;

        public double getMinCoverage() {
            return minCoverage < 0 || minCoverage > 1 ? 0.8 : minCoverage;
        }

        public void setMinCoverage(double minCoverage) {
            this.minCoverage = minCoverage;
        }

        public Integer getMaxTotalTokens() {
            return maxTotalTokens == null || maxTotalTokens <= 0 ? null : maxTotalTokens;
        }

        public void setMaxTotalTokens(Integer maxTotalTokens) {
            this.maxTotalTokens = maxTotalTokens;
        }

        public String getPricingVersion() {
            return pricingVersion == null || pricingVersion.isBlank() ? null : pricingVersion;
        }

        public void setPricingVersion(String pricingVersion) {
            this.pricingVersion = pricingVersion;
        }

        public Double getInputUsdPer1kTokens() {
            return inputUsdPer1kTokens;
        }

        public void setInputUsdPer1kTokens(Double inputUsdPer1kTokens) {
            this.inputUsdPer1kTokens = inputUsdPer1kTokens;
        }

        public Double getOutputUsdPer1kTokens() {
            return outputUsdPer1kTokens;
        }

        public void setOutputUsdPer1kTokens(Double outputUsdPer1kTokens) {
            this.outputUsdPer1kTokens = outputUsdPer1kTokens;
        }
    }

    /**
     * Package-member revisit cooldown and MOVE oscillation window. Clock starts
     * on visit leave. STAY / still-at-current is not a revisit. This is not
     * actual-member consumption; Task A enforces the checks.
     */
    public static class OpportunityRevisit {
        private int pointCooldownMinutes = 45;
        private int pathCooldownMinutes = 45;
        private int zonePackageCooldownMinutes = 60;
        private int oscillationWindowMoves = 3;
        private final MaterialEligibility materialEligibility = new MaterialEligibility();

        public int getPointCooldownMinutes() {
            return pointCooldownMinutes <= 0 ? 45 : pointCooldownMinutes;
        }

        public void setPointCooldownMinutes(int pointCooldownMinutes) {
            this.pointCooldownMinutes = pointCooldownMinutes;
        }

        public int getPathCooldownMinutes() {
            return pathCooldownMinutes <= 0 ? 45 : pathCooldownMinutes;
        }

        public void setPathCooldownMinutes(int pathCooldownMinutes) {
            this.pathCooldownMinutes = pathCooldownMinutes;
        }

        public int getZonePackageCooldownMinutes() {
            return zonePackageCooldownMinutes <= 0 ? 60 : zonePackageCooldownMinutes;
        }

        public void setZonePackageCooldownMinutes(int zonePackageCooldownMinutes) {
            this.zonePackageCooldownMinutes = zonePackageCooldownMinutes;
        }

        public int getOscillationWindowMoves() {
            return oscillationWindowMoves <= 0 ? 3 : oscillationWindowMoves;
        }

        public void setOscillationWindowMoves(int oscillationWindowMoves) {
            this.oscillationWindowMoves = oscillationWindowMoves;
        }

        public MaterialEligibility getMaterialEligibility() {
            return materialEligibility;
        }

        /**
         * Which signals reopen MOVE_OSCILLATION / cooled eligibility.
         * Cooldown expiry is automatic when its flag is true. BITE/FISH_ON at
         * the current location justify STAY only and must not reopen MOVE-back.
         */
        public static class MaterialEligibility {
            private boolean cooldownExpiry = true;
            private boolean timeBucketChange = true;
            private boolean weatherChange = true;
            private boolean livePressureChange = true;
            private boolean newToolEvidence = true;
            private boolean explicitUserIntent = true;
            private boolean biteOrFishOnAtReturnTarget = false;
            private int timeChangeMinutes = 60;
            private double windSpeedChangeKph = 10.0;
            private double pressureChangeHpa = 2.0;

            public boolean isCooldownExpiry() {
                return cooldownExpiry;
            }

            public void setCooldownExpiry(boolean cooldownExpiry) {
                this.cooldownExpiry = cooldownExpiry;
            }

            public boolean isTimeBucketChange() {
                return timeBucketChange;
            }

            public void setTimeBucketChange(boolean timeBucketChange) {
                this.timeBucketChange = timeBucketChange;
            }

            public boolean isWeatherChange() {
                return weatherChange;
            }

            public void setWeatherChange(boolean weatherChange) {
                this.weatherChange = weatherChange;
            }

            public boolean isLivePressureChange() {
                return livePressureChange;
            }

            public void setLivePressureChange(boolean livePressureChange) {
                this.livePressureChange = livePressureChange;
            }

            public boolean isNewToolEvidence() {
                return newToolEvidence;
            }

            public void setNewToolEvidence(boolean newToolEvidence) {
                this.newToolEvidence = newToolEvidence;
            }

            public boolean isExplicitUserIntent() {
                return explicitUserIntent;
            }

            public void setExplicitUserIntent(boolean explicitUserIntent) {
                this.explicitUserIntent = explicitUserIntent;
            }

            public boolean isBiteOrFishOnAtReturnTarget() {
                return biteOrFishOnAtReturnTarget;
            }

            public void setBiteOrFishOnAtReturnTarget(boolean biteOrFishOnAtReturnTarget) {
                this.biteOrFishOnAtReturnTarget = biteOrFishOnAtReturnTarget;
            }

            public int getTimeChangeMinutes() {
                return timeChangeMinutes <= 0 ? 60 : timeChangeMinutes;
            }

            public void setTimeChangeMinutes(int timeChangeMinutes) {
                this.timeChangeMinutes = timeChangeMinutes;
            }

            public double getWindSpeedChangeKph() {
                return windSpeedChangeKph <= 0 ? 10.0 : windSpeedChangeKph;
            }

            public void setWindSpeedChangeKph(double windSpeedChangeKph) {
                this.windSpeedChangeKph = windSpeedChangeKph;
            }

            public double getPressureChangeHpa() {
                return pressureChangeHpa <= 0 ? 2.0 : pressureChangeHpa;
            }

            public void setPressureChangeHpa(double pressureChangeHpa) {
                this.pressureChangeHpa = pressureChangeHpa;
            }
        }
    }
}
