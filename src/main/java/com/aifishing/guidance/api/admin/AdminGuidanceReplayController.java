package com.aifishing.guidance.api.admin;

import com.aifishing.guidance.replay.AgentRunEvaluate;
import com.aifishing.guidance.replay.AgentRunEvaluateResponse;
import com.aifishing.guidance.replay.AgentRunTraceLoader;
import com.aifishing.guidance.replay.AgentRunTraceResponse;
import com.aifishing.guidance.replay.SessionMapTraceQuery;
import com.aifishing.guidance.replay.SessionMapTraceResponse;
import com.aifishing.guidance.replay.SessionTimelineQuery;
import com.aifishing.guidance.replay.SessionTimelineResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/guidance")
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminGuidanceReplayController {

    private final SessionTimelineQuery sessionTimelineQuery;
    private final SessionMapTraceQuery sessionMapTraceQuery;
    private final AgentRunTraceLoader agentRunTraceLoader;
    private final AgentRunEvaluate agentRunEvaluate;

    public AdminGuidanceReplayController(
            SessionTimelineQuery sessionTimelineQuery,
            SessionMapTraceQuery sessionMapTraceQuery,
            AgentRunTraceLoader agentRunTraceLoader,
            AgentRunEvaluate agentRunEvaluate
    ) {
        this.sessionTimelineQuery = sessionTimelineQuery;
        this.sessionMapTraceQuery = sessionMapTraceQuery;
        this.agentRunTraceLoader = agentRunTraceLoader;
        this.agentRunEvaluate = agentRunEvaluate;
    }

    @GetMapping("/sessions/{sessionId}/timeline")
    public SessionTimelineResponse timeline(@PathVariable UUID sessionId) {
        return sessionTimelineQuery.load(sessionId);
    }

    @GetMapping("/sessions/{sessionId}/map-trace")
    public SessionMapTraceResponse mapTrace(@PathVariable UUID sessionId) {
        return sessionMapTraceQuery.load(sessionId);
    }

    @GetMapping("/runs/{runId}")
    public AgentRunTraceResponse run(@PathVariable UUID runId) {
        return agentRunTraceLoader.load(runId);
    }

    @PostMapping("/runs/{runId}/evaluate")
    public AgentRunEvaluateResponse evaluate(@PathVariable UUID runId) {
        return agentRunEvaluate.evaluate(runId);
    }
}
