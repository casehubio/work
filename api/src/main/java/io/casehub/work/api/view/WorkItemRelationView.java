package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record WorkItemRelationView(UUID id, UUID sourceId, UUID targetId, String relationType, String createdBy,
        Instant createdAt) {
}
