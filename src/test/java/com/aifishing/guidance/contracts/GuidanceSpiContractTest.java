package com.aifishing.guidance.contracts;

import com.aifishing.guidance.runtime.AgentRunMetadata;
import com.aifishing.guidance.runtime.AgentRunSnapshot;
import com.aifishing.guidance.runtime.EnvironmentSnapshot;
import com.aifishing.guidance.runtime.ModelTurnResult;
import com.aifishing.guidance.runtime.OptionalUserInput;
import com.aifishing.guidance.runtime.GuidanceRuntimeConfiguration;
import com.aifishing.guidance.spi.DecisionPersistence;
import com.aifishing.guidance.spi.DecisionValidator;
import com.aifishing.guidance.spi.EnvironmentSnapshotResolver;
import com.aifishing.guidance.spi.FishingAgentFacade;
import com.aifishing.guidance.spi.FishingAgentRuntime;
import com.aifishing.guidance.spi.DerivedTriggerEvaluator;
import com.aifishing.guidance.spi.FishingSessionStateBuilder;
import com.aifishing.guidance.spi.TriggerRouter;
import com.aifishing.guidance.tools.GuidanceToolsConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceSpiContractTest {

    @Test
    void productionFacadeIsSessionTriggerOptionalInput() throws Exception {
        Method run = FishingAgentFacade.class.getMethod(
                "run", UUID.class, GuidanceTrigger.class, OptionalUserInput.class);
        assertThat(run.getReturnType()).isEqualTo(AgentRunResult.class);
    }

    @Test
    void replayApiTakesImmutableSnapshot() throws Exception {
        Method execute = FishingAgentRuntime.class.getMethod("execute", AgentRunSnapshot.class);
        assertThat(execute.getReturnType()).isEqualTo(AgentRunResult.class);
        assertThat(Arrays.stream(FishingAgentRuntime.class.getMethods()).map(Method::getName).toList())
                .doesNotContain("run");
    }

    @Test
    void environmentResolverTakesSessionId() throws Exception {
        Method resolve = EnvironmentSnapshotResolver.class.getMethod("resolve", UUID.class);
        assertThat(resolve.getReturnType()).isEqualTo(EnvironmentSnapshot.class);
    }

    @Test
    void stateBuilderTakesSessionAndEnvironment() throws Exception {
        Method build = FishingSessionStateBuilder.class.getMethod(
                "build", UUID.class, EnvironmentSnapshot.class);
        assertThat(build.getReturnType()).isEqualTo(FishingSessionState.class);
    }

    @Test
    void contextBuilderTakesRetrievedMemory() throws Exception {
        Method build = com.aifishing.guidance.spi.FishingAgentContextBuilder.class.getMethod(
                "build",
                FishingSessionState.class,
                GuidanceTrigger.class,
                RetrievedMemory.class);
        assertThat(build.getReturnType()).isEqualTo(FishingAgentContext.class);
    }

    @Test
    void memoryRetrievalIsSessionStateTrigger() throws Exception {
        Method retrieve = com.aifishing.guidance.spi.MemoryRetrievalService.class.getMethod(
                "retrieve", UUID.class, FishingSessionState.class, GuidanceTrigger.class);
        assertThat(retrieve.getReturnType()).isEqualTo(RetrievedMemory.class);
    }

    @Test
    void validatorTakesCandidateStateAndSafetyVerdict() throws Exception {
        Method validate = DecisionValidator.class.getMethod(
                "validate", CandidateDecision.class, FishingSessionState.class, SafetyVerdict.class);
        assertThat(validate.getReturnType()).isEqualTo(DecisionValidationResult.class);
    }

    @Test
    void persistenceIsRunningAppendFinalize() throws Exception {
        List<String> names = Arrays.stream(DecisionPersistence.class.getMethods())
                .map(Method::getName)
                .toList();
        assertThat(names).contains(
                "createRunning",
                "appendStateSnapshot",
                "appendContextSnapshot",
                "appendToolCall",
                "appendCandidate",
                "appendValidation",
                "finalize",
                "current"
        );
        assertThat(names).doesNotContain("save");
        assertThat(DecisionPersistence.class.getMethod(
                "createRunning",
                UUID.class,
                UUID.class,
                GuidanceTrigger.class,
                String.class,
                AgentRunMetadata.class
        ).getReturnType()).isEqualTo(void.class);
        assertThat(DecisionPersistence.class.getMethod(
                "finalize", UUID.class, AgentRunStatus.class, DeliveredDecision.class
        ).getReturnType()).isEqualTo(void.class);
    }

    @Test
    void triggerRouterReturnsOptionalRoutingDecision() throws Exception {
        Method route = TriggerRouter.class.getMethod("route", SessionEvent.class, FishingSessionState.class);
        assertThat(route.getReturnType()).isEqualTo(java.util.Optional.class);
        assertThat(route.getGenericReturnType().getTypeName()).isEqualTo(
                "java.util.Optional<com.aifishing.guidance.contracts.TriggerRoutingDecision>");
    }

    @Test
    void derivedTriggerEvaluatorTakesStateOnly() throws Exception {
        Method evaluate = DerivedTriggerEvaluator.class.getMethod("evaluate", FishingSessionState.class);
        assertThat(evaluate.getReturnType()).isEqualTo(java.util.Optional.class);
        assertThat(evaluate.getGenericReturnType().getTypeName()).isEqualTo(
                "java.util.Optional<com.aifishing.guidance.contracts.TriggerRoutingDecision>");
        assertThat(Arrays.stream(DerivedTriggerEvaluator.class.getMethods()).map(Method::getParameterCount).toList())
                .containsExactly(1);
    }

    @Test
    void triggerRouterAndEvaluatorHaveNoProductionBean() {
        for (Class<?> type : List.of(TriggerRouter.class, DerivedTriggerEvaluator.class)) {
            assertThat(type.getAnnotation(Component.class)).isNull();
            assertThat(type.getAnnotation(Service.class)).isNull();
        }
        for (Class<?> config : List.of(GuidanceRuntimeConfiguration.class, GuidanceToolsConfiguration.class)) {
            assertThat(Arrays.stream(config.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Bean.class))
                    .map(Method::getReturnType))
                    .doesNotContain(TriggerRouter.class, DerivedTriggerEvaluator.class);
        }
    }

    @Test
    void snapshotLoaderTakesRunId() throws Exception {
        Method load = com.aifishing.guidance.spi.AgentRunSnapshotLoader.class.getMethod("load", UUID.class);
        assertThat(load.getReturnType()).isEqualTo(java.util.Optional.class);
        assertThat(load.getGenericReturnType().getTypeName()).isEqualTo(
                "java.util.Optional<com.aifishing.guidance.contracts.FrozenAgentRunSnapshot>");
    }

    @Test
    void evalRuntimeIsEvalModeAndDoesNotWriteDeliveredDecision() throws Exception {
        Method execute = com.aifishing.guidance.spi.EvalRuntime.class.getMethod(
                "execute", FrozenAgentRunSnapshot.class, ReplayMode.class);
        assertThat(execute.getReturnType()).isEqualTo(AgentRunResult.class);
        assertThat(com.aifishing.guidance.spi.EvalRuntime.class.getMethod("executionMode").getReturnType())
                .isEqualTo(EvalExecutionMode.class);
        assertThat(Arrays.stream(com.aifishing.guidance.spi.EvalRuntime.class.getMethods()).map(Method::getName))
                .doesNotContain("finalize");
        assertThat(DecisionPersistence.class.getMethod(
                "finalize", UUID.class, AgentRunStatus.class, DeliveredDecision.class
        ).getReturnType()).isEqualTo(void.class);
    }

    @Test
    void evalToolRegistryIsReadOnlyAgentToolRegistry() {
        assertThat(com.aifishing.guidance.spi.AgentToolRegistry.class)
                .isAssignableFrom(com.aifishing.guidance.spi.EvalToolRegistry.class);
        assertThat(com.aifishing.guidance.spi.EvalToolRegistry.class.getDeclaredMethods())
                .extracting(Method::getName)
                .doesNotContain("executeWrite", "enqueue", "persist");
    }

    @Test
    void runtimeTypesStayProviderNeutral() {
        for (Class<?> type : List.of(
                EnvironmentSnapshot.class,
                AgentRunSnapshot.class,
                ModelTurnResult.class,
                OptionalUserInput.class,
                AgentRunMetadata.class
        )) {
            assertThat(type.getName()).doesNotContain("openai", "bedrock", "anthropic");
        }
    }

}
