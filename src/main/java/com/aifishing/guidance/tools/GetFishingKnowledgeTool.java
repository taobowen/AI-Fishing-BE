package com.aifishing.guidance.tools;

import com.aifishing.guidance.contracts.GetFishingKnowledgeParams;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.spi.AgentTool;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Structured short knowledge from existing strategy/tactics vocabulary. No free-web invention.
 */
@Component
public class GetFishingKnowledgeTool implements AgentTool {

    private final Clock clock;

    public GetFishingKnowledgeTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ToolName name() {
        return ToolName.GET_FISHING_KNOWLEDGE;
    }

    @Override
    public ToolResultEnvelope execute(ToolRequestEnvelope request) {
        AgentToolSupport.Parsed<GetFishingKnowledgeParams> parsed = AgentToolSupport.parse(
                request, name(), "GetFishingKnowledgeParams", GetFishingKnowledgeParams.class, clock);
        if (!parsed.valid()) {
            return parsed.error();
        }
        try {
            GetFishingKnowledgeParams params = parsed.params();
            List<FishingKnowledgeCatalog.Entry> entries =
                    FishingKnowledgeCatalog.lookup(params.query(), params.targetSpecies());
            if (entries.isEmpty()) {
                return AgentToolSupport.unknown(name(), clock);
            }
            ObjectNode data = GuidanceContracts.mapper().createObjectNode();
            data.put("query", params.query());
            if (params.targetSpecies() != null) {
                data.put("targetSpecies", params.targetSpecies().name());
            }
            ArrayNode items = data.putArray("entries");
            for (FishingKnowledgeCatalog.Entry entry : entries) {
                ObjectNode item = items.addObject();
                item.put("term", entry.term());
                item.put("kind", entry.kind());
                item.put("summary", entry.summary());
            }
            return AgentToolSupport.ok(name(), clock, data);
        } catch (RuntimeException ex) {
            return AgentToolSupport.error(name(), clock);
        }
    }
}
