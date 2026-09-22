package io.casehub.work.api.view;

import java.util.UUID;

public record AddDefinitionResult(UUID id, String path, String scope) {
}
