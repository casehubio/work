package io.casehub.work.api.view;

import java.time.Instant;

public record UpdateDeadlineRequest(Instant newDeadline) {
}
