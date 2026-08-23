package io.casehub.work.progress;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubtreeRollbackResult(
    UUID operationId,
    UUID rootId,
    Instant targetTimestamp,
    List<NodeRollbackOutcome> outcomes
) {}
