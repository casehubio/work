package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.work.progress.ConditionEvaluator;
import io.casehub.work.progress.StepDefinition;
import io.casehub.work.progress.StepStatus;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class StepValidator {

    private static final Map<StepStatus, List<StepStatus>> VALID_TRANSITIONS = Map.of(
            StepStatus.PENDING, List.of(StepStatus.ACTIVE),
            StepStatus.ACTIVE, List.of(StepStatus.COMPLETED, StepStatus.SKIPPED, StepStatus.FAILED),
            StepStatus.COMPLETED, List.of(StepStatus.ACTIVE),
            StepStatus.FAILED, List.of(StepStatus.ACTIVE),
            StepStatus.SKIPPED, List.of()
    );

    public void validateTransition(String stepName, StepStatus from, StepStatus to,
                                   List<StepDefinition> definitions, JsonNode currentState,
                                   ConditionEvaluator conditionEvaluator) {
        if (from.isTerminal()) {
            throw new IllegalStateException(
                    "Step '" + stepName + "' is in terminal status " + from + " — cannot transition");
        }

        List<StepStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, List.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Invalid step transition: " + from + " -> " + to + " for step '" + stepName + "'");
        }

        StepDefinition def = definitions.stream()
                .filter(d -> d.name().equals(stepName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown step: " + stepName));

        if (to == StepStatus.SKIPPED && !def.optional()) {
            throw new IllegalStateException(
                    "Step '" + stepName + "' is not optional — cannot skip");
        }

        if (to == StepStatus.ACTIVE && from == StepStatus.PENDING) {
            validateDependencies(stepName, def, definitions, currentState);
            validateCondition(stepName, def, currentState, conditionEvaluator);
        }
    }

    public boolean isDerivedCompletion(List<StepDefinition> definitions, JsonNode state) {
        JsonNode steps = state.get("steps");
        if (steps == null) return false;

        for (StepDefinition def : definitions) {
            if (def.optional()) continue;
            JsonNode stepNode = steps.get(def.name());
            if (stepNode == null) return false;
            String statusStr = stepNode.has("status") ? stepNode.get("status").asText() : "pending";
            StepStatus status = StepStatus.valueOf(statusStr.toUpperCase(Locale.ROOT));
            if (!status.isDone()) return false;
        }
        return true;
    }

    private void validateDependencies(String stepName, StepDefinition def,
                                      List<StepDefinition> definitions, JsonNode state) {
        JsonNode steps = state.get("steps");
        Map<String, StepDefinition> defsByName = definitions.stream()
                .collect(Collectors.toMap(StepDefinition::name, d -> d));

        for (String depName : def.dependsOn()) {
            JsonNode depNode = steps != null ? steps.get(depName) : null;
            if (depNode == null) {
                throw new IllegalStateException(
                        "Step '" + stepName + "' has unsatisfied dependency '" + depName + "' (not found in state)");
            }
            String statusStr = depNode.has("status") ? depNode.get("status").asText() : "pending";
            StepStatus depStatus = StepStatus.valueOf(statusStr.toUpperCase(Locale.ROOT));
            if (!depStatus.isDone()) {
                throw new IllegalStateException(
                        "Step '" + stepName + "' has unsatisfied dependency '" + depName
                                + "' (status: " + depStatus + ")");
            }
        }
    }

    private void validateCondition(String stepName, StepDefinition def, JsonNode state,
                                   ConditionEvaluator conditionEvaluator) {
        if (def.condition() == null || def.condition().isBlank()) return;
        if (!conditionEvaluator.evaluate(def.condition(), state)) {
            throw new IllegalStateException(
                    "Step '" + stepName + "' condition not satisfied: " + def.condition());
        }
    }
}
