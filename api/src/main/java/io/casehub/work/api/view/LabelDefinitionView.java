package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record LabelDefinitionView(UUID id, String path, UUID vocabularyId, String scope,
                                  String description, String createdBy, Instant createdAt) {
}
