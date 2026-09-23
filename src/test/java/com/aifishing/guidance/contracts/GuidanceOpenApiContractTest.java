package com.aifishing.guidance.contracts;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceOpenApiContractTest {

    @Test
    void staticSpecRefsCanonicalDefsAndIsNotLiveMerged() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("guidance/openapi/guidance-v1.yaml")) {
            assertThat(in).isNotNull();
            Object loaded = new Yaml().load(in);
            List<String> refs = new ArrayList<>();
            collectRefs(loaded, refs);
            assertThat(refs).isNotEmpty();
            assertThat(refs).allMatch(ref ->
                    ref.startsWith("../contracts/guidance-contracts.v1.json#/$defs/")
                            || ref.startsWith("#/components/"));
            assertThat(refs).anyMatch(ref -> ref.endsWith("GuidanceDecisionRequest"));
            assertThat(refs).anyMatch(ref -> ref.endsWith("GuidanceCurrentResponse"));
            assertThat(refs).anyMatch(ref -> ref.endsWith("GuidanceFeedbackRequest"));
            assertThat(refs).anyMatch(ref -> ref.endsWith("DeliveredDecision") || ref.endsWith("GuidanceCurrentResponse"));
            assertThat(refs).anyMatch(ref -> ref.endsWith("WindDirectionBucket"));
            assertThat(refs).anyMatch(ref -> ref.endsWith("OutcomeAttribution"));
            assertThat(refs).anyMatch(ref -> ref.endsWith("LiveWaypointActivity"));
            assertThat(refs).noneMatch(ref -> internalEvalDefs().stream().anyMatch(ref::endsWith));
        }
    }

    @Test
    void publicOpenApiDoesNotPublishInternalEvalDefs() {
        assertThat(internalEvalDefs()).isNotEmpty().allMatch(name ->
                GuidanceContracts.def(name).path("x-internal").asBoolean(false));
        assertThat(GuidanceContracts.def("LearningJobType").path("x-internal").asBoolean(false)).isFalse();
        assertThat(GuidanceContracts.def("WindDirectionBucket").path("x-internal").asBoolean(false)).isFalse();
    }

    private static List<String> internalEvalDefs() {
        List<String> names = new ArrayList<>();
        var defs = GuidanceContracts.bundle().path("$defs");
        defs.fieldNames().forEachRemaining(name -> {
            if (defs.get(name).path("x-internal").asBoolean(false)) {
                names.add(name);
            }
        });
        return names;
    }

    @SuppressWarnings("unchecked")
    private static void collectRefs(Object node, List<String> refs) {
        Deque<Object> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            Object current = stack.pop();
            if (current instanceof Map<?, ?> map) {
                Object ref = map.get("$ref");
                if (ref instanceof String value) {
                    refs.add(value);
                }
                map.values().forEach(stack::push);
            } else if (current instanceof List<?> list) {
                list.forEach(stack::push);
            }
        }
    }
}
