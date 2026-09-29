package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record EscalationSummaryView(UUID id, UUID workItemId, String eventType,
                                    String summary, Instant generatedAt) {
}
