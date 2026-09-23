package com.aifishing.guidance.versions;

import com.aifishing.guidance.GuidanceProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Catalog of <em>available</em> Agent policy versions from YAML. This is not the
 * live production/candidate switch (Task C / {@code AgentRuntimeControl}).
 */
@Component
public class AgentPolicyRegistry {

    private final Map<String, AgentPolicyVersion> versions;
    private final String defaultVersion;

    public AgentPolicyRegistry(GuidanceProperties properties) {
        Objects.requireNonNull(properties, "properties");
        GuidanceProperties.Policy policy = properties.getPolicy();
        Map<String, AgentPolicyVersion> indexed = new LinkedHashMap<>();
        if (policy != null && policy.getVersions() != null) {
            for (Map.Entry<String, GuidanceProperties.Policy.VersionSpec> entry : policy.getVersions().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                GuidanceProperties.Policy.VersionSpec spec = entry.getValue();
                AgentPolicyVersion catalog = new AgentPolicyVersion(
                        entry.getKey(),
                        spec.getPromptProfile(),
                        spec.getContextProfile(),
                        spec.getToolsetProfile(),
                        spec.getModelProfile(),
                        spec.getLearningProfile()
                );
                indexed.put(catalog.version(), catalog);
            }
        }
        if (indexed.isEmpty()) {
            throw new IllegalStateException("app.guidance.policy.versions must list at least one available version");
        }
        this.versions = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
        String configuredDefault = policy == null ? null : policy.getDefaultVersion();
        this.defaultVersion = configuredDefault == null || configuredDefault.isBlank()
                ? AgentPolicyVersion.V1
                : configuredDefault.trim();
        if (!this.versions.containsKey(this.defaultVersion)) {
            throw new IllegalStateException(
                    "app.guidance.policy.default-version is not an available version: " + this.defaultVersion
            );
        }
    }

    /**
     * YAML default catalog version. Live production is {@code AgentRuntimeControl}.
     */
    public String defaultVersion() {
        return defaultVersion;
    }

    public AgentPolicyVersion require(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Agent policy version is required");
        }
        AgentPolicyVersion found = versions.get(version.trim());
        if (found == null) {
            throw new IllegalArgumentException("Unknown agent policy version: " + version);
        }
        return found;
    }

    public List<AgentPolicyVersion> all() {
        return new ArrayList<>(versions.values());
    }
}
