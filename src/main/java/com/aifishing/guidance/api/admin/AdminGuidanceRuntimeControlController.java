package com.aifishing.guidance.api.admin;

import com.aifishing.guidance.control.AgentRuntimeControl;
import com.aifishing.guidance.control.AgentRuntimeControlPatch;
import com.aifishing.guidance.control.AgentRuntimeControlStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/guidance/runtime-control")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminGuidanceRuntimeControlController {

    private final AgentRuntimeControlStore store;

    public AdminGuidanceRuntimeControlController(AgentRuntimeControlStore store) {
        this.store = store;
    }

    @GetMapping
    public AgentRuntimeControlResponse get() {
        return toResponse(store.load());
    }

    @PatchMapping
    public AgentRuntimeControlResponse patch(@RequestBody(required = false) AgentRuntimeControlPatchRequest request) {
        AgentRuntimeControlPatchRequest body = request == null
                ? new AgentRuntimeControlPatchRequest(null, null, null, null, null)
                : request;
        return toResponse(store.update(new AgentRuntimeControlPatch(
                body.agentEnabled(),
                body.productionVersion(),
                body.candidateVersion(),
                body.shadowEnabled(),
                body.learningEnabled()
        )));
    }

    private static AgentRuntimeControlResponse toResponse(AgentRuntimeControl control) {
        return new AgentRuntimeControlResponse(
                control.agentEnabled(),
                control.productionVersion(),
                control.candidateVersion(),
                control.shadowEnabled(),
                control.learningEnabled(),
                control.updatedAt()
        );
    }
}
