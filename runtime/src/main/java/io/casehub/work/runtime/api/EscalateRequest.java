package io.casehub.work.runtime.api;

public record EscalateRequest(String targetGroup, String reason) {
}
