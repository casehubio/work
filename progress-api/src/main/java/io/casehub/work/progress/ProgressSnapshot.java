package io.casehub.work.progress;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ProgressSnapshot(
        UUID eventId,
        JsonNode state,
        ProgressStatus status,
        ProgressChangeType changeType,
        Instant timestamp
) {}
