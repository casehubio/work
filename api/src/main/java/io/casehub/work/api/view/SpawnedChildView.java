package io.casehub.work.api.view;

import java.util.UUID;

public record SpawnedChildView(UUID workItemId, String callerRef) {
}
