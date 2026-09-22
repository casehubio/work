package io.casehub.work.api.view;

public record LinkIssueRequest(String trackerType, String externalRef, String linkedBy) {
}
