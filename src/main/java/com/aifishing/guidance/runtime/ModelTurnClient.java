package com.aifishing.guidance.runtime;

/**
 * Provider-neutral model adapter. Implementations must not leak vendor types
 * into spi/domain. OpenAI Responses is one adapter, not the loop.
 */
public interface ModelTurnClient {

    String provider();

    String modelName();

    String modelVersion();

    ModelTurnResult nextTurn(ModelTurnInput input);
}
