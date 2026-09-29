package io.casehub.work.api.view;

import java.util.UUID;

public record CreateQueueResult(UUID id, String name, String labelPattern) {
}
