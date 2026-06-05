package io.casehub.work.runtime.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.Outcome;
import io.casehub.work.runtime.event.WorkItemContextBuilder;
import io.casehub.work.runtime.filter.JexlConditionEvaluator;
import io.casehub.work.runtime.model.OutcomeCodecs;
import io.casehub.work.runtime.model.WorkItem;

/**
 * Validates a submitted outcome name against a WorkItem's permitted outcomes list,
 * and evaluates any JEXL condition declared on the matching outcome.
 *
 * <p>
 * Extracted from {@code WorkItemService.validateOutcome()} to keep lifecycle transition
 * logic focused and to enable independent testing of condition evaluation.
 */
@ApplicationScoped
public class OutcomeValidator {

    @Inject
    JexlConditionEvaluator conditionEvaluator;

    /**
     * Validates the submitted outcome name and evaluates its condition if present.
     *
     * <p>
     * No-op when {@code item.permittedOutcomes} is null (unconstrained WorkItem).
     *
     * @param item       the WorkItem being completed or rejected
     * @param outcome    the submitted outcome name
     * @param resolution the completion payload; null on reject paths
     * @param reason     the rejection reason; null on complete paths
     * @param actorId    who is completing or rejecting
     * @throws IllegalArgumentException if outcome is absent, too long, not in the permitted list,
     *                                  or its condition is not satisfied
     * @throws IllegalStateException    if {@code permittedOutcomes} is non-null but fails to decode
     *                                  (data integrity error)
     */
    public void validate(final WorkItem item, final String outcome,
            final String resolution, final String reason, final String actorId) {
        if (item.permittedOutcomes == null) {
            return;
        }
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException(
                    "outcome is required — this WorkItem was created from a template that declares named outcomes");
        }
        if (outcome.length() > 255) {
            throw new IllegalArgumentException("outcome exceeds maximum length of 255 characters");
        }

        final List<Outcome> definitions = OutcomeCodecs.decodePermittedOutcomes(item.permittedOutcomes);
        if (definitions == null) {
            throw new IllegalStateException(
                    "permittedOutcomes on WorkItem " + item.id + " is non-null but failed to decode — data integrity error");
        }

        final Optional<Outcome> match = definitions.stream()
                .filter(o -> outcome.equals(o.name()))
                .findFirst();

        if (match.isEmpty()) {
            final List<String> names = definitions.stream().map(Outcome::name).toList();
            throw new IllegalArgumentException(
                    "outcome '" + outcome + "' is not permitted; allowed values: " + names);
        }

        final String condition = match.get().condition();
        if (condition != null && !condition.isBlank()) {
            final Map<String, Object> workItemContext = WorkItemContextBuilder.toMap(item);
            final Map<String, Object> extra = new HashMap<>();
            extra.put("resolution", resolution);
            extra.put("reason", reason);
            extra.put("actorId", actorId);
            if (!conditionEvaluator.evaluate(condition, extra, workItemContext)) {
                throw new IllegalArgumentException(
                        "outcome '" + outcome + "' condition not satisfied (check template definition)");
            }
        }
    }
}
