package io.casehub.work.api.view;

import java.util.Map;

public record SpawnChildRequest(
        String templateId,
        String callerRef,
        Map<String, Object> overrides) {
}
