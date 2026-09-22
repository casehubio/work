package io.casehub.work.api.view;

import java.time.Instant;

public record SkillProfileView(String workerId, String narrative, Instant createdAt, Instant updatedAt) {
}
