package io.casehub.work.annotations.runtime;

import io.casehub.work.api.WorkItemPriority;
import java.util.List;

public record HumanApprovalDescriptor(
    String title,
    List<String> candidateGroups,
    List<String> candidateUsers,
    WorkItemPriority priority,
    String claimDeadline,
    String expiresAt,
    String description,
    String methodName,
    String declaringClass,
    String returnTypeName,
    List<String> types,
    List<String> labels
) {}
