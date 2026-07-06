package io.casehub.work.rest;

import java.time.Instant;

public record ExtendRequest(Instant newExpiresAt) {
}
