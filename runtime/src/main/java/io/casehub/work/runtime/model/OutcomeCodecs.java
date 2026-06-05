package io.casehub.work.runtime.model;

import java.util.List;
import java.util.stream.StreamSupport;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.casehub.work.api.Outcome;

/**
 * JSON encoding and decoding utilities for outcome fields on {@link WorkItem} and
 * {@link WorkItemTemplate}.
 *
 * <p>
 * Pure static utilities — no CDI, no JPA. Safe to use from any layer (mapping,
 * service, event) without introducing layering violations.
 *
 * <p>
 * Both {@link WorkItemTemplate#outcomes} and {@link WorkItem#permittedOutcomes} are stored
 * as JSON arrays of {@link Outcome} objects. {@code encodeOutcomes()} and
 * {@code decodeOutcomes()} serve both columns. {@code decodePermittedOutcomes()} handles
 * a legacy string-array format for WorkItems created before this encoding was introduced.
 */
public final class OutcomeCodecs {

    private static final Logger LOG = Logger.getLogger(OutcomeCodecs.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OutcomeCodecs() {
    }

    /**
     * Encodes a list of {@link Outcome} objects to JSON for storage on either
     * {@link WorkItemTemplate#outcomes} or {@link WorkItem#permittedOutcomes}.
     *
     * @param outcomes list to encode; null or empty returns null
     * @return JSON string, or null
     */
    public static String encodeOutcomes(final List<Outcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(outcomes);
        } catch (final Exception e) {
            LOG.warnf("Failed to encode outcomes: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Decodes {@link WorkItemTemplate#outcomes} JSON to a list of {@link Outcome} objects.
     *
     * @param outcomesJson JSON string; null or blank returns empty list
     * @return list of outcomes, or empty list if unconstrained or unparseable
     */
    public static List<Outcome> decodeOutcomes(final String outcomesJson) {
        if (outcomesJson == null || outcomesJson.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(outcomesJson, new TypeReference<>() {});
        } catch (final Exception e) {
            LOG.warnf("Failed to decode outcomes JSON: %s", e.getMessage());
            return List.of();
        }
    }

    /**
     * Decodes {@link WorkItem#permittedOutcomes} JSON to a list of {@link Outcome} objects.
     *
     * <p>
     * Handles two storage formats:
     * <ul>
     *   <li><b>New format</b> (object array): {@code [{"name":"approved","displayName":"...","condition":"..."}]}</li>
     *   <li><b>Legacy format</b> (string array): {@code ["approved","rejected"]} — wrapped into
     *       {@code Outcome(name, null, null)} objects on decode. Written by pre-#177 instantiation paths.</li>
     * </ul>
     *
     * <p>
     * Format detection uses {@code readTree()} to parse the JSON and inspect the first element's
     * node type — never exception-as-control-flow.
     *
     * @param permittedOutcomesJson JSON string; null or blank returns null
     * @return list of {@link Outcome} objects, or null if unconstrained or unparseable
     */
    public static List<Outcome> decodePermittedOutcomes(final String permittedOutcomesJson) {
        if (permittedOutcomesJson == null || permittedOutcomesJson.isBlank()) {
            return null;
        }
        try {
            final var node = MAPPER.readTree(permittedOutcomesJson);
            if (!node.isArray()) {
                LOG.warnf("permittedOutcomes JSON is not an array (type: %s) — data integrity error", node.getNodeType());
                return null;
            }
            final ArrayNode arr = (ArrayNode) node;
            if (arr.isEmpty() || arr.get(0).isObject()) {
                // new format: array of Outcome objects (or empty array — encodeOutcomes returns null for empty, so this is unreachable in practice)
                return MAPPER.convertValue(arr, new TypeReference<>() {});
            } else {
                // legacy format: array of name strings — wrap with null displayName/condition
                return StreamSupport.stream(arr.spliterator(), false)
                        .map(n -> new Outcome(n.asText(), null, null))
                        .toList();
            }
        } catch (final Exception e) {
            LOG.warnf("Failed to decode permittedOutcomes JSON: %s", e.getMessage());
            return null;
        }
    }
}
