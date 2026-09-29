package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record IssueLinkView(UUID id, UUID workItemId, String trackerType, String externalRef,
                             String title, String url, String status,
                             Instant linkedAt, String linkedBy) {
}
