package io.casehub.work.api.view;

public record EscalateRequest(String targetGroup, String reason) {
}
