package io.casehub.work.rest;

public record EscalateRequest(String targetGroup, String reason) {
}
