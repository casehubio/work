package io.casehub.work.api.view;

public record CreateIssueRequest(String trackerType, String title, String body, String linkedBy) {
}
