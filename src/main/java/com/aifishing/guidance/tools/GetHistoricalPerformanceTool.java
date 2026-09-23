package com.aifishing.guidance.tools;

import com.aifishing.guidance.contracts.GetHistoricalPerformanceParams;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.HistoricalPerformance;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.empirical.HistoricalPerformanceQuery;
import com.aifishing.guidance.spi.AgentTool;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Cross-session empirical memory. Returns {@link HistoricalPerformance} after SUM-then-recompute
 * backoff. Empty or failed lookups are UNKNOWN.
 */
@Component
public class GetHistoricalPerformanceTool implements AgentTool {

    private final HistoricalPerformanceQuery query;
    private final Clock clock;

    public GetHistoricalPerformanceTool(HistoricalPerformanceQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    @Override
    public ToolName name() {
        return ToolName.GET_HISTORICAL_PERFORMANCE;
    }

    @Override
    public ToolResultEnvelope execute(ToolRequestEnvelope request) {
        AgentToolSupport.Parsed<GetHistoricalPerformanceParams> parsed = AgentToolSupport.parse(
                request, name(), "GetHistoricalPerformanceParams", GetHistoricalPerformanceParams.class, clock);
        if (!parsed.valid()) {
            return parsed.error();
        }
        try {
            HistoricalPerformance performance = query.lookup(parsed.params()).orElse(null);
            if (performance == null) {
                return AgentToolSupport.unknown(name(), clock);
            }
            return AgentToolSupport.ok(name(), clock, GuidanceContracts.mapper().valueToTree(performance));
        } catch (RuntimeException ex) {
            return AgentToolSupport.unknown(name(), clock);
        }
    }
}
