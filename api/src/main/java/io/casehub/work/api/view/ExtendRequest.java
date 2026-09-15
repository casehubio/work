package io.casehub.work.api.view;

import java.time.Instant;

public record ExtendRequest(Instant newExpiresAt) {
}
