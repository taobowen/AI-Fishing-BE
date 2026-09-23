package com.aifishing.guidance.persist;

import com.aifishing.common.exception.BadRequestException;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.control.AgentRuntimeControl;
import com.aifishing.guidance.control.AgentRuntimeControlPatch;
import com.aifishing.guidance.persistence.AgentRuntimeControlEntity;
import com.aifishing.guidance.persistence.AgentRuntimeControlRepository;
import com.aifishing.guidance.versions.AgentPolicyRegistry;
import com.aifishing.guidance.versions.AgentPolicyVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaAgentRuntimeControlStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-18T16:00:00Z");

    @Mock
    private AgentRuntimeControlRepository repository;

    private JpaAgentRuntimeControlStore store;

    @BeforeEach
    void setUp() {
        store = new JpaAgentRuntimeControlStore(
                repository,
                new AgentPolicyRegistry(properties()),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void unknownProductionVersionIsRejected() {
        when(repository.findById(AgentRuntimeControl.SINGLETON_ID)).thenReturn(Optional.of(seeded()));

        assertThatThrownBy(() -> store.update(new AgentRuntimeControlPatch(null, "v99", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown agent policy version");
    }

    @Test
    void unknownCandidateVersionIsRejected() {
        when(repository.findById(AgentRuntimeControl.SINGLETON_ID)).thenReturn(Optional.of(seeded()));

        assertThatThrownBy(() -> store.update(new AgentRuntimeControlPatch(null, null, "nope", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown agent policy version");
    }

    @Test
    void validPatchWritesControlAndDoesNotTouchAgentRuns() {
        when(repository.findById(AgentRuntimeControl.SINGLETON_ID)).thenReturn(Optional.of(seeded()));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AgentRuntimeControl updated = store.update(new AgentRuntimeControlPatch(
                false, AgentPolicyVersion.V2, AgentPolicyVersion.V1, true, false
        ));

        assertThat(updated.agentEnabled()).isFalse();
        assertThat(updated.productionVersion()).isEqualTo(AgentPolicyVersion.V2);
        assertThat(updated.candidateVersion()).isEqualTo(AgentPolicyVersion.V1);
        assertThat(updated.shadowEnabled()).isTrue();
        assertThat(updated.learningEnabled()).isFalse();
        assertThat(updated.updatedAt()).isEqualTo(NOW);

        ArgumentCaptor<AgentRuntimeControlEntity> saved = ArgumentCaptor.forClass(AgentRuntimeControlEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(AgentRuntimeControl.SINGLETON_ID);
        assertThat(saved.getValue().getProductionVersion()).isEqualTo(AgentPolicyVersion.V2);
    }

    @Test
    void blankCandidateClearsCandidate() {
        AgentRuntimeControlEntity entity = seeded();
        entity.setCandidateVersion(AgentPolicyVersion.V2);
        when(repository.findById(AgentRuntimeControl.SINGLETON_ID)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AgentRuntimeControl updated = store.update(new AgentRuntimeControlPatch(null, null, "  ", null, null));

        assertThat(updated.candidateVersion()).isNull();
    }

    @Test
    void missingRowFailsRequire() {
        when(repository.findById(AgentRuntimeControl.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(store::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    private static AgentRuntimeControlEntity seeded() {
        AgentRuntimeControlEntity entity = new AgentRuntimeControlEntity();
        entity.setId(AgentRuntimeControl.SINGLETON_ID);
        entity.setAgentEnabled(true);
        entity.setProductionVersion(AgentPolicyVersion.V1);
        entity.setCandidateVersion(null);
        entity.setShadowEnabled(false);
        entity.setLearningEnabled(true);
        entity.setUpdatedAt(Instant.parse("2026-09-18T14:00:00Z"));
        return entity;
    }

    private static GuidanceProperties properties() {
        return new GuidanceProperties();
    }
}
