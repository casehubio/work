package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record SpawnGroupSummaryView(
        UUID id,
        UUID parentId,
        String idempotencyKey,
        Instant createdAt) {
}
