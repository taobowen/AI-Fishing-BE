package com.aifishing.guidance.runtime;

import com.aifishing.common.openai.OpenAiResponsesClient;
import com.aifishing.common.openai.OpenAiResponsesRequest;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.versions.PromptProfile;
import com.aifishing.lake.processing.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI Responses adapter only. Does not fall back to the deterministic client
 * when configuration or the call fails — the runtime finalizes a defined fallback.
 */
public final class OpenAiResponsesModelTurnClient implements ModelTurnClient {

    public static final String PROVIDER = "openai";

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesModelTurnClient.class);

    private final OpenAiProperties openAiProperties;
    private final OpenAiResponsesClient responsesClient;
    private final GuidanceProperties guidanceProperties;
    private final Clock clock;

    public OpenAiResponsesModelTurnClient(
            OpenAiProperties openAiProperties,
            OpenAiResponsesClient responsesClient,
            GuidanceProperties guidanceProperties,
            Clock clock
    ) {
        this.openAiProperties = openAiProperties;
        this.responsesClient = responsesClient;
        this.guidanceProperties = guidanceProperties;
        this.clock = clock;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String modelName() {
        return openAiProperties.getModel();
    }

    @Override
    public String modelVersion() {
        return openAiProperties.getModel();
    }

    @Override
    public ModelTurnResult nextTurn(ModelTurnInput input) {
        if (!openAiProperties.isConfigured()) {
            return new ModelTurnResult.Error(
                    "CONFIG",
                    "OPENAI_API_KEY / app.openai.api-key is not configured"
            );
        }
        if (openAiProperties.getModel() == null || openAiProperties.getModel().isBlank()) {
            return new ModelTurnResult.Error("CONFIG", "app.openai.model is not configured");
        }
        long timeoutMs = modelTimeoutMs(input.runDeadline());
        if (timeoutMs <= 0) {
            return new ModelTurnResult.Incomplete("MODEL_CALL_TIMEOUT");
        }
        try {
            OpenAiResponsesRequest.Result parsed = CompletableFuture
                    .supplyAsync(() -> complete(input))
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .get();
            return parse(parsed);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new ModelTurnResult.Error("INTERRUPTED", "model call interrupted");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof TimeoutException) {
                return new ModelTurnResult.Incomplete("MODEL_CALL_TIMEOUT");
            }
            log.warn("OpenAI guidance turn failed: {}", rootMessage(cause));
            return new ModelTurnResult.Error("MODEL_CALL", rootMessage(cause));
        } catch (RuntimeException ex) {
            log.warn("OpenAI guidance turn failed: {}", rootMessage(ex));
            return new ModelTurnResult.Error("MODEL_CALL", rootMessage(ex));
        }
    }

    private OpenAiResponsesRequest.Result complete(ModelTurnInput input) {
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = GuidanceContracts.mapper().convertValue(
                GuidanceContracts.structuredOutputSchema("CandidateDecision"),
                Map.class
        );
        return responsesClient.complete(new OpenAiResponsesRequest(
                openAiProperties.getModel(),
                Math.min(openAiProperties.getMaxTokens(), 2000),
                instructions(input),
                inputJson(input),
                "CandidateDecision",
                schema,
                false,
                false
        ));
    }

    private ModelTurnResult parse(OpenAiResponsesRequest.Result parsed) {
        if (parsed == null || parsed.json() == null || parsed.json().isBlank()) {
            return new ModelTurnResult.Incomplete("empty model output");
        }
        try {
            JsonNode tree = GuidanceContracts.mapper().readTree(parsed.json());
            if (tree.path("refusal").isTextual() && !tree.path("refusal").asText().isBlank()) {
                return new ModelTurnResult.Refusal(tree.path("refusal").asText());
            }
            CandidateDecision decision = GuidanceContracts.mapper().treeToValue(tree, CandidateDecision.class);
            if (decision == null || decision.primaryAction() == null) {
                return new ModelTurnResult.Incomplete("model output was not a CandidateDecision");
            }
            return new ModelTurnResult.FinalCandidateDecision(decision);
        } catch (Exception ex) {
            return new ModelTurnResult.Error("PARSE", rootMessage(ex));
        }
    }

    private String inputJson(ModelTurnInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trigger", input.trigger());
        payload.put("state", input.state());
        payload.put("context", input.context());
        payload.put("userNote", input.userInput() == null ? null : input.userInput().userNote());
        payload.put("clientHints", input.userInput() == null ? null : input.userInput().clientHints());
        payload.put("observations", input.observations());
        payload.put("turnIndex", input.turnIndex());
        payload.put("hintPolicy", "clientHints are non-authoritative; server state wins");
        try {
            return GuidanceContracts.mapper().writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize model input", ex);
        }
    }

    static String instructions() {
        return PromptProfile.guidanceV1().instructions();
    }

    private static String instructions(ModelTurnInput input) {
        if (input != null && input.promptInstructions() != null && !input.promptInstructions().isBlank()) {
            return input.promptInstructions();
        }
        return instructions();
    }

    private long modelTimeoutMs(Instant runDeadline) {
        long configured = Math.max(1L, guidanceProperties.getModelCallTimeoutMs());
        if (runDeadline == null) {
            return configured;
        }
        long remaining = Duration.between(clock.instant(), runDeadline).toMillis();
        return Math.min(configured, Math.max(0L, remaining));
    }

    private static String rootMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "unavailable";
        }
        String message = error.getMessage();
        int newline = message.indexOf('\n');
        String first = newline < 0 ? message : message.substring(0, newline);
        return first.length() > 400 ? first.substring(0, 400) : first;
    }
}
