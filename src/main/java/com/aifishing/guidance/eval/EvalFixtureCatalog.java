package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecomputedComponent;
import com.aifishing.guidance.contracts.ReplayMode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Loads eval fixtures from {@code guidance/eval/fixtures/{platform,policy}/}.
 */
public final class EvalFixtureCatalog {

    public static final String PLATFORM_PATH = "classpath*:guidance/eval/fixtures/platform/*.json";
    public static final String POLICY_PATH = "classpath*:guidance/eval/fixtures/policy/*.json";

    private EvalFixtureCatalog() {
    }

    public static List<EvalCaseFixture> platform() {
        return load(PLATFORM_PATH);
    }

    public static List<EvalCaseFixture> policy() {
        return load(POLICY_PATH);
    }

    public static List<EvalCaseFixture> load(String locationPattern) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(locationPattern);
            List<EvalCaseFixture> fixtures = new ArrayList<>();
            for (Resource resource : resources) {
                if (resource == null || !resource.exists() || !resource.isReadable()) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    fixtures.add(parse(GuidanceContracts.mapper().readTree(in)));
                }
            }
            fixtures.sort(Comparator.comparing(EvalCaseFixture::caseId));
            return List.copyOf(fixtures);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load eval fixtures from " + locationPattern, ex);
        }
    }

    static EvalCaseFixture parse(JsonNode node) {
        FrozenAgentRunSnapshot snapshot = null;
        if (node.hasNonNull("snapshot")) {
            snapshot = GuidanceContracts.mapper().convertValue(node.get("snapshot"), FrozenAgentRunSnapshot.class);
        } else if (node.hasNonNull("snapshotRef")) {
            snapshot = EvalFixtures.byRef(node.get("snapshotRef").asText());
        }
        return new EvalCaseFixture(
                text(node, "caseId"),
                enumValue(node, "kind", EvalSuiteKind.class),
                enumValue(node, "replayMode", ReplayMode.class),
                enums(node, "recomputedComponents", RecomputedComponent.class),
                enums(node, "allowedActions", GuidanceAction.class),
                enums(node, "forbiddenActions", GuidanceAction.class),
                strings(node, "invariants"),
                enumValue(node, "exactPrimaryAction", GuidanceAction.class),
                strings(node, "rubric"),
                snapshot,
                node.hasNonNull("sourceRunId") ? UUID.fromString(node.get("sourceRunId").asText()) : null,
                enumValue(node, "recordedPrimaryAction", GuidanceAction.class),
                node.hasNonNull("recordedTargetTripWaypointId")
                        ? UUID.fromString(node.get("recordedTargetTripWaypointId").asText())
                        : null,
                enumValue(node, "recordedOutcomeKind", OutcomeKind.class),
                text(node, "scenarioId"),
                node.hasNonNull("seed") ? node.get("seed").asLong() : null,
                text(node, "skipReason")
        );
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        return Enum.valueOf(type, node.get(field).asText());
    }

    private static <E extends Enum<E>> List<E> enums(JsonNode node, String field, Class<E> type) {
        if (!node.has(field) || !node.get(field).isArray()) {
            return List.of();
        }
        List<E> values = new ArrayList<>();
        node.get(field).forEach(item -> values.add(Enum.valueOf(type, item.asText())));
        return values;
    }

    private static List<String> strings(JsonNode node, String field) {
        if (!node.has(field) || !node.get(field).isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.get(field).forEach(item -> values.add(item.asText()));
        return values;
    }
}
