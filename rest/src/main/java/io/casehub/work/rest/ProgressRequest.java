package io.casehub.work.rest;

public record ProgressRequest(Integer percentComplete, String statusNote) {
}
