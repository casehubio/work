package io.casehub.work.rest;

import io.casehub.work.api.LabelPersistence;

public record WorkItemLabelResponse(
        String path,
        LabelPersistence persistence,
        String appliedBy) {
}
