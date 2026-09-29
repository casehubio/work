package io.casehub.work.api.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpawnGroupView(UUID id, UUID parentId, String idempotencyKey, Instant createdAt,
                             List<SpawnGroupChildView> children) {
}
