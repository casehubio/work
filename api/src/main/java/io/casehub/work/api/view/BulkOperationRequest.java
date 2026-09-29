package io.casehub.work.api.view;

import java.util.List;

public record BulkOperationRequest(
        String operation,
        List<String> workItemIds,
        String actorId,
        String reason) {
}
