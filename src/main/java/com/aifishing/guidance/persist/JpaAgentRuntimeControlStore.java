package com.aifishing.guidance.persist;

import com.aifishing.common.exception.BadRequestException;
import com.aifishing.guidance.control.AgentRuntimeControl;
import com.aifishing.guidance.control.AgentRuntimeControlPatch;
import com.aifishing.guidance.control.AgentRuntimeControlStore;
import com.aifishing.guidance.persistence.AgentRuntimeControlEntity;
import com.aifishing.guidance.persistence.AgentRuntimeControlRepository;
import com.aifishing.guidance.versions.AgentPolicyRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/**
 * Singleton control row. Version ids must exist in {@link AgentPolicyRegistry}.
 * Never rewrites {@code agent_runs}.
 */
@Component
public class JpaAgentRuntimeControlStore implements AgentRuntimeControlStore {

    private final AgentRuntimeControlRepository repository;
    private final AgentPolicyRegistry policyRegistry;
    private final Clock clock;

    public JpaAgentRuntimeControlStore(
            AgentRuntimeControlRepository repository,
            AgentPolicyRegistry policyRegistry,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public AgentRuntimeControl load() {
        return require();
    }

    @Override
    @Transactional(readOnly = true)
    public AgentRuntimeControl require() {
        return repository.findById(AgentRuntimeControl.SINGLETON_ID)
                .map(JpaAgentRuntimeControlStore::toModel)
                .orElseThrow(() -> new IllegalStateException("agent_runtime_control singleton row is missing"));
    }

    @Override
    @Transactional
    public AgentRuntimeControl update(AgentRuntimeControlPatch patch) {
        Objects.requireNonNull(patch, "patch");
        AgentRuntimeControlEntity entity = repository.findById(AgentRuntimeControl.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("agent_runtime_control singleton row is missing"));
        if (patch.agentEnabled() != null) {
            entity.setAgentEnabled(patch.agentEnabled());
        }
        if (patch.productionVersion() != null) {
            entity.setProductionVersion(requireVersion(patch.productionVersion(), "productionVersion"));
        }
        if (patch.candidateVersion() != null) {
            String candidate = patch.candidateVersion().isBlank() ? null : patch.candidateVersion().trim();
            entity.setCandidateVersion(candidate == null ? null : requireVersion(candidate, "candidateVersion"));
        }
        if (patch.shadowEnabled() != null) {
            entity.setShadowEnabled(patch.shadowEnabled());
        }
        if (patch.learningEnabled() != null) {
            entity.setLearningEnabled(patch.learningEnabled());
        }
        requireVersion(entity.getProductionVersion(), "productionVersion");
        if (entity.getCandidateVersion() != null) {
            requireVersion(entity.getCandidateVersion(), "candidateVersion");
        }
        entity.setUpdatedAt(clock.instant());
        return toModel(repository.saveAndFlush(entity));
    }

    private String requireVersion(String version, String field) {
        if (version == null || version.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        try {
            return policyRegistry.require(version.trim()).version();
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    private static AgentRuntimeControl toModel(AgentRuntimeControlEntity entity) {
        return new AgentRuntimeControl(
                entity.isAgentEnabled(),
                entity.getProductionVersion(),
                entity.getCandidateVersion(),
                entity.isShadowEnabled(),
                entity.isLearningEnabled(),
                entity.getUpdatedAt()
        );
    }
}
