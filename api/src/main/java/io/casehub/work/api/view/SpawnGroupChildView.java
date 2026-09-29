package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record SpawnGroupChildView(UUID workItemId, Instant createdAt) {
}
