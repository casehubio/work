package io.casehub.work.api.view;

import java.util.List;

public record SpawnBodyRequest(
        String idempotencyKey,
        List<SpawnChildRequest> children) {
}
