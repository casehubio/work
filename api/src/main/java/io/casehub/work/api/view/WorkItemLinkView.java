package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record WorkItemLinkView(UUID id, String url, String title, String relationType, String linkedBy,
        Instant createdAt) {
}
