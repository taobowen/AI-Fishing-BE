package com.aifishing.guidance.contracts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GuidanceContracts {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final JsonNode BUNDLE = loadBundle();
    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private static final Map<String, JsonSchema> SCHEMAS = new ConcurrentHashMap<>();

    private GuidanceContracts() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static JsonNode bundle() {
        return BUNDLE;
    }

    public static JsonNode def(String name) {
        JsonNode node = BUNDLE.path("$defs").get(name);
        if (node == null || node.isMissingNode()) {
            throw new IllegalArgumentException("Unknown guidance contract: " + name);
        }
        return node;
    }

    public static JsonSchema schema(String defName) {
        def(defName);
        return SCHEMAS.computeIfAbsent(defName, GuidanceContracts::compile);
    }

    /**
     * Schema sent to OpenAI structured outputs. Omits URL {@code $id}/{@code $schema}
     * (those fail {@code ^[a-zA-Z0-9_-]{1,64}$}) and keeps only reachable {@code $defs}.
     */
    public static JsonNode structuredOutputSchema(String defName) {
        JsonNode root = def(defName).deepCopy();
        if (!(root instanceof ObjectNode wrapper)) {
            throw new IllegalArgumentException("Guidance contract is not an object: " + defName);
        }
        ObjectNode defs = reachableDefs(defName);
        defs.remove(defName);
        if (!defs.isEmpty()) {
            wrapper.set("$defs", defs);
        }
        return wrapper;
    }

    private static ObjectNode reachableDefs(String rootName) {
        ObjectNode defs = MAPPER.createObjectNode();
        JsonNode all = BUNDLE.get("$defs");
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootName);
        while (!queue.isEmpty()) {
            String name = queue.removeFirst();
            if (defs.has(name)) {
                continue;
            }
            JsonNode node = all == null ? null : all.get(name);
            if (node == null || node.isMissingNode()) {
                continue;
            }
            defs.set(name, node.deepCopy());
            collectRefs(node, queue);
        }
        return defs;
    }

    private static void collectRefs(JsonNode node, Deque<String> queue) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual()) {
                String value = ref.asText();
                String prefix = "#/$defs/";
                if (value.startsWith(prefix)) {
                    queue.add(value.substring(prefix.length()));
                }
            }
            node.fields().forEachRemaining(field -> collectRefs(field.getValue(), queue));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectRefs(child, queue));
        }
    }

    private static JsonSchema compile(String defName) {
        ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        wrapper.put("$id", GuidanceSchemaVersion.BUNDLE_ID + "#compiled/" + defName);
        wrapper.put("$ref", "#/$defs/" + defName);
        wrapper.set("$defs", BUNDLE.get("$defs").deepCopy());
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();
        return SCHEMA_FACTORY.getSchema(wrapper, config);
    }

    private static JsonNode loadBundle() {
        try (InputStream in = GuidanceContracts.class.getClassLoader()
                .getResourceAsStream(GuidanceSchemaVersion.BUNDLE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + GuidanceSchemaVersion.BUNDLE_RESOURCE);
            }
            return MAPPER.readTree(in);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
