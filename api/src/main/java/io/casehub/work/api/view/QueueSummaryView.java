package io.casehub.work.api.view;

import java.util.UUID;

import io.casehub.work.api.WorkItemSummary;

public record QueueSummaryView(UUID id, String name, String labelPattern, String scope,
                               WorkItemSummary summary) {
}
