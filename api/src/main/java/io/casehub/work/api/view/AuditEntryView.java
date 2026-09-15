package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryView(UUID id, String event, String actor, String detail, Instant occurredAt) {
}
