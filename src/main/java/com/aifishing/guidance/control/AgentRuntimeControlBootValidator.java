package com.aifishing.guidance.control;

import com.aifishing.guidance.versions.AgentPolicyRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fail startup if the control row names a version that is not in the YAML catalog.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentRuntimeControlBootValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeControlBootValidator.class);

    private final AgentRuntimeControlStore store;
    private final AgentPolicyRegistry policyRegistry;

    public AgentRuntimeControlBootValidator(
            AgentRuntimeControlStore store,
            AgentPolicyRegistry policyRegistry
    ) {
        this.store = store;
        this.policyRegistry = policyRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        AgentRuntimeControl control = store.require();
        policyRegistry.require(control.productionVersion());
        if (control.candidateVersion() != null && !control.candidateVersion().isBlank()) {
            policyRegistry.require(control.candidateVersion());
        }
        log.info(
                "Agent runtime control loaded: production={} candidate={} agentEnabled={} shadowEnabled={} learningEnabled={}",
                control.productionVersion(),
                control.candidateVersion(),
                control.agentEnabled(),
                control.shadowEnabled(),
                control.learningEnabled()
        );
    }
}
