package io.casehub.work.rest;

import java.time.Instant;

public record UpdateDeadlineRequest(Instant newDeadline) {
}
