package io.casehub.work.graphql.dto;

import io.casehub.work.api.WorkItemLifecycleEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Type;

@Type("WorkItemLifecycleEvent")
public record WorkItemLifecycleEventType(
    UUID workItemId,
    String type,
    String status,
    String actor,
    String detail,
    String outcome,
    String tenancyId,
    String assigneeId,
    String callerRef,
    List<String> types,
    Instant occurredAt) {

  public static WorkItemLifecycleEventType from(WorkItemLifecycleEvent event) {
    return new WorkItemLifecycleEventType(
        event.workItemId(),
        event.type(),
        event.status() != null ? event.status().name() : null,
        event.actor(),
        event.detail(),
        event.outcome(),
        event.tenancyId(),
        event.assigneeId(),
        event.callerRef(),
        event.types(),
        event.occurredAt());
  }
}
