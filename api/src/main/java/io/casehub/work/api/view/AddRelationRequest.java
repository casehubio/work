package io.casehub.work.api.view;

import java.util.UUID;

public record AddRelationRequest(UUID targetId, String relationType) {
}
