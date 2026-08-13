package io.casehub.work.api;

public record WorkItemLabel(String path, LabelPersistence persistence, String appliedBy) {}
