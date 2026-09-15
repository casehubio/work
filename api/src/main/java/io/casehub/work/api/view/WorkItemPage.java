package io.casehub.work.api.view;

import java.util.List;

public record WorkItemPage(List<WorkItemView> items, long totalCount, boolean hasMore) {
}
