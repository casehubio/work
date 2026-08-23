package io.casehub.work.progress;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record NodeRollbackOutcome(
    UUID progressId,
    Outcome outcome,
    String reason,
    JsonNode previousState,
    JsonNode restoredState,
    boolean policyBypassed
) {
    public enum Outcome { ROLLED_BACK, SKIPPED, FAILED }
}
