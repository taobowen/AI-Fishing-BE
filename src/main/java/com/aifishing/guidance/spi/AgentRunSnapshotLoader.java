package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Loads a frozen agent-run snapshot for eval / shadow replay.
 * Implementations must return recorded state, retrieved memory, tool observations,
 * and clock — not current live weather or memory.
 */
public interface AgentRunSnapshotLoader {

    Optional<FrozenAgentRunSnapshot> load(UUID runId);
}
