package io.casehub.work.progress;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.platform.api.routing.NamedStrategy;

public interface CustomRollbackDetector extends NamedStrategy {
    boolean isRollback(JsonNode previousState, JsonNode currentState, JsonNode definition);
}
