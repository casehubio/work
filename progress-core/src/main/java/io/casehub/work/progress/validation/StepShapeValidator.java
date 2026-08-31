package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.work.progress.StepDefinition;
import io.casehub.work.progress.StepStatus;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class StepShapeValidator implements ShapeValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final StepDefinitionValidator definitionValidator = new StepDefinitionValidator();

    @Override
    public String shapeType() {
        return "step";
    }

    @Override
    public void validate(JsonNode state, JsonNode definition) {
        if (definition == null || definition.isMissingNode()) {
            throw new IllegalArgumentException("Step shape requires a 'definition' field");
        }
        JsonNode stepsNode = state.get("steps");
        if (stepsNode == null || !stepsNode.isObject()) {
            throw new IllegalArgumentException("Step state requires a 'steps' object");
        }

        List<StepDefinition> stepDefs = parseDefinition(definition);
        Set<String> definedNames = stepDefs.stream()
                .map(StepDefinition::name)
                .collect(Collectors.toSet());

        var fields = stepsNode.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String stepName = entry.getKey();
            if (!definedNames.contains(stepName)) {
                throw new IllegalArgumentException("Unknown step in state: " + stepName);
            }
            JsonNode stepState = entry.getValue();
            if (!stepState.has("status")) {
                throw new IllegalArgumentException("Step '" + stepName + "' missing 'status' field");
            }
            String statusStr = stepState.get("status").asText();
            try {
                StepStatus.valueOf(statusStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Step '" + stepName + "' has invalid status: " + statusStr);
            }
        }
    }

    public void validateDefinition(JsonNode definition) {
        List<StepDefinition> stepDefs = parseDefinition(definition);
        definitionValidator.validate(stepDefs);
    }

    public static List<StepDefinition> parseDefinition(JsonNode definition) {
        try {
            JsonNode steps = definition.isObject() && definition.has("steps")
                             ? definition.get("steps")
                             : definition;
            return MAPPER.readerForListOf(StepDefinition.class).readValue(steps);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid step definition: " + e.getMessage(), e);
        }
    }
}
