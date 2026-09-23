package com.aifishing.guidance.tools;

import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps tool envelopes contract-safe: no stack-trace keys, byte cap, empty → UNKNOWN.
 */
final class ToolResultSanitizer {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "stackTrace",
            "stack_trace",
            "targetWaypointId",
            "confidence",
            "depth"
    );

    private ToolResultSanitizer() {
    }

    static JsonNode stripForbiddenKeys(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode copy = GuidanceContracts.mapper().createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (FORBIDDEN_KEYS.contains(field.getKey())) {
                    continue;
                }
                JsonNode child = stripForbiddenKeys(field.getValue());
                if (child != null) {
                    copy.set(field.getKey(), child);
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = GuidanceContracts.mapper().createArrayNode();
            for (JsonNode item : node) {
                JsonNode child = stripForbiddenKeys(item);
                copy.add(child == null ? GuidanceContracts.mapper().nullNode() : child);
            }
            return copy;
        }
        return node;
    }

    static boolean isEmptyData(JsonNode data) {
        return data == null || data.isNull() || data.isMissingNode()
                || (data.isObject() && data.isEmpty())
                || (data.isArray() && data.isEmpty());
    }

    static String sanitizeErrorType(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return ToolErrorType.INTERNAL;
        }
        String trimmed = errorType.trim();
        if (trimmed.length() > 64
                || trimmed.indexOf('\n') >= 0
                || trimmed.indexOf('\r') >= 0
                || trimmed.contains("at ")
                || trimmed.contains("Exception")
                || trimmed.contains("stack")) {
            return ToolErrorType.INTERNAL;
        }
        return trimmed;
    }

    static int utf8Bytes(JsonNode node) {
        if (node == null) {
            return 0;
        }
        try {
            return GuidanceContracts.mapper().writeValueAsBytes(node).length;
        } catch (JsonProcessingException ex) {
            return Integer.MAX_VALUE;
        }
    }

    static JsonNode truncateToBudget(JsonNode data, int maxBytes) {
        if (data == null || utf8Bytes(data) <= maxBytes) {
            return data;
        }
        if (data.isObject()) {
            ObjectNode slim = data.deepCopy();
            List<String> names = new ArrayList<>();
            slim.fieldNames().forEachRemaining(names::add);
            for (int i = names.size() - 1; i >= 0 && utf8Bytes(slim) > maxBytes; i--) {
                slim.remove(names.get(i));
            }
            if (utf8Bytes(slim) <= maxBytes) {
                if (!slim.has("truncated")) {
                    slim.put("truncated", true);
                    if (utf8Bytes(slim) > maxBytes) {
                        slim.remove("truncated");
                    }
                }
                return slim;
            }
        }
        ObjectNode marker = GuidanceContracts.mapper().createObjectNode();
        marker.put("truncated", true);
        if (utf8Bytes(marker) <= maxBytes) {
            return marker;
        }
        return null;
    }

    static ToolResultStatus normalizeEmptyOk(ToolResultStatus status, JsonNode data) {
        if (status == ToolResultStatus.OK && isEmptyData(data)) {
            return ToolResultStatus.UNKNOWN;
        }
        return status;
    }
}
