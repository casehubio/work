package io.casehub.work.api.view;

import io.casehub.work.api.LabelPersistence;

public record WorkItemLabelView(String path, LabelPersistence persistence, String appliedBy) {
}
