package io.casehub.work.api.view;

import java.util.List;
import java.util.UUID;

public record SpawnResultView(
        UUID groupId,
        List<SpawnedChildView> children,
        boolean created) {
}
