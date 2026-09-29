package io.casehub.work.api.view;

import java.util.List;
import java.util.UUID;

public record InstancesView(UUID parentId, UUID groupId, int instanceCount, int requiredCount,
                            int completedCount, int rejectedCount, String groupStatus,
                            List<WorkItemView> instances) {
}
