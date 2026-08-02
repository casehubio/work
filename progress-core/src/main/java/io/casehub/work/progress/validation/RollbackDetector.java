package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
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

    public boolean isRollback(String shapeType, JsonNode previousState, JsonNode currentState) {
        if (previousState == null) return false;

        return switch (shapeType) {
            case "percentage" -> isPercentageRollback(previousState, currentState);
            case "count" -> isCountRollback(previousState, currentState);
            case "step" -> isStepRollback(previousState, currentState);
            default -> false;
        };
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
        if (prevSteps.isMissingNode() || currSteps.isMissingNode()) return false;

        Iterator<Map.Entry<String, JsonNode>> fields = currSteps.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String stepName = entry.getKey();
            JsonNode prevStep = prevSteps.get(stepName);
            if (prevStep == null) continue;

            String prevStatus = prevStep.path("status").asText("pending").toLowerCase(Locale.ROOT);
            String currStatus = entry.getValue().path("status").asText("pending").toLowerCase(Locale.ROOT);

            int prevOrd = STEP_STATUS_ORDINALS.getOrDefault(prevStatus, 0);
            int currOrd = STEP_STATUS_ORDINALS.getOrDefault(currStatus, 0);

            if (currOrd < prevOrd) return true;
        }
        return false;
    }
}
