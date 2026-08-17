package io.casehub.work.graphql.dto;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemLabel;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Type;

@Type("WorkItem")
public record WorkItemType(
    UUID id,
    String tenancyId,
    String title,
    String description,
    String status,
    String priority,
    String assigneeId,
    String owner,
    String candidateGroups,
    String createdBy,
    String payload,
    String resolution,
    String outcome,
    String callerRef,
    UUID parentId,
    String scope,
    Set<String> types,
    List<String> labels,
    Instant claimDeadline,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt,
    Instant assignedAt,
    Instant startedAt,
    Instant completedAt) {

  public static WorkItemType from(WorkItem item) {
    return new WorkItemType(
        item.id(),
        item.tenancyId(),
        item.title(),
        item.description(),
        item.status() != null ? item.status().name() : null,
        item.priority() != null ? item.priority().name() : null,
        item.assigneeId(),
        item.owner(),
        item.candidateGroups(),
        item.createdBy(),
        item.payload(),
        item.resolution(),
        item.outcome(),
        item.callerRef(),
        item.parentId(),
        item.scope(),
        item.types(),
        item.labels() != null ? item.labels().stream().map(WorkItemLabel::path).toList() : List.of(),
        item.claimDeadline(),
        item.expiresAt(),
        item.createdAt(),
        item.updatedAt(),
        item.assignedAt(),
        item.startedAt(),
        item.completedAt());
  }
}
