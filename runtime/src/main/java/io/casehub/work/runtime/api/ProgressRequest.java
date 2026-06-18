package io.casehub.work.runtime.api;

public record ProgressRequest(Integer percentComplete, String statusNote) {
}
