package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.work.progress.CustomRollbackDetector;

import java.util.Iterator;
import java.util.function.Function;
import java.util.Locale;
import java.util.Map;

public class RollbackDetector {

    private static final Map<String, Integer> STEP_STATUS_ORDINALS = Map.of(
            "pending", 0,
            "active", 1,
            "completed", 2,
            "skipped", 2,
            "failed", 2
                                                                           );

    private final Function<String, CustomRollbackDetector> detectorResolver;

    public RollbackDetector() {
        this(id -> {throw new IllegalArgumentException("No CustomRollbackDetector resolver configured");});
    }

    public RollbackDetector(Function<String, CustomRollbackDetector> detectorResolver) {
        this.detectorResolver = detectorResolver;
    }

    public boolean isRollback(String shapeType, JsonNode previousState, JsonNode currentState, JsonNode definition) {
        if (previousState == null) {return false;}

        return switch (shapeType) {
            case "percentage" -> isPercentageRollback(previousState, currentState);
            case "count" -> isCountRollback(previousState, currentState);
            case "step" -> isStepRollback(previousState, currentState);
            case "custom" -> isCustomRollback(previousState, currentState, definition);
            default -> false;
        };
    }

    private boolean isCustomRollback(JsonNode prev, JsonNode curr, JsonNode definition) {
        if (definition == null) {return false;}

        if (definition.has("rollbackDetectorId") && !definition.get("rollbackDetectorId").isNull()) {
            String                 detectorId = definition.get("rollbackDetectorId").asText();
            CustomRollbackDetector detector   = detectorResolver.apply(detectorId);
            return detector.isRollback(prev, curr, definition);
        }

        if (definition.has("rollbackField") && !definition.get("rollbackField").isNull()) {
            String   fieldName = definition.get("rollbackField").asText();
            JsonNode prevField = prev.get(fieldName);
            JsonNode currField = curr.get(fieldName);
            if (prevField == null || currField == null || !prevField.isNumber() || !currField.isNumber()) {
                return false;
            }
            return currField.doubleValue() < prevField.doubleValue();
        }

        return false;
    }

    private boolean isPercentageRollback(JsonNode prev, JsonNode curr) {
        int prevValue = prev.path("value").asInt(0);
        int currValue = curr.path("value").asInt(0);
        return currValue < prevValue;
    }

    private boolean isCountRollback(JsonNode prev, JsonNode curr) {
        int prevCurrent = prev.path("current").asInt(0);
        int currCurrent = curr.path("current").asInt(0);
        return currCurrent < prevCurrent;
    }

    private boolean isStepRollback(JsonNode prev, JsonNode curr) {
        JsonNode prevSteps = prev.path("steps");
        JsonNode currSteps = curr.path("steps");
        if (prevSteps.isMissingNode() || currSteps.isMissingNode()) {return false;}

        Iterator<Map.Entry<String, JsonNode>> fields = currSteps.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry    = fields.next();
            String                      stepName = entry.getKey();
            JsonNode                    prevStep = prevSteps.get(stepName);
            if (prevStep == null) {continue;}

            String prevStatus = prevStep.path("status").asText("pending").toLowerCase(Locale.ROOT);
            String currStatus = entry.getValue().path("status").asText("pending").toLowerCase(Locale.ROOT);

            int prevOrd = STEP_STATUS_ORDINALS.getOrDefault(prevStatus, 0);
            int currOrd = STEP_STATUS_ORDINALS.getOrDefault(currStatus, 0);

            if (currOrd < prevOrd) {return true;}
        }
        return false;
    }
}
