package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record WorkItemNoteView(UUID id, String author, String content, Instant createdAt, Instant editedAt) {
}
