package io.casehub.work.postgres.broadcaster;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemStatus;

/**
 * Wire-format DTO for a {@link WorkItemLifecycleEvent} sent over a PostgreSQL NOTIFY channel.
 *
 * <p>
 * Contains only the scalar fields that are safe to serialize across nodes. The {@code workItem}
 * JPA entity is excluded — it is not available on receiving nodes, and SSE clients never see
 * it (it is {@code @JsonIgnore} in the event class). Converting back to a lifecycle event
 * uses {@link WorkItemLifecycleEvent#fromWire}.
 *
 * <p>
 * {@code tenancyId} is included in the wire format for server-to-server relay (broadcaster
 * filtering), but is NOT exposed to SSE clients ({@code @JsonIgnore} in
 * {@link WorkItemLifecycleEvent}).
 */
public record WorkItemEventPayload(
        @JsonProperty("type") String type,
        @JsonProperty("source") String source,
        @JsonProperty("subject") String subject,
        @JsonProperty("workItemId") UUID workItemId,
        @JsonProperty("status") WorkItemStatus status,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("actor") String actor,
        @JsonProperty("detail") String detail,
        @JsonProperty("rationale") String rationale,
        @JsonProperty("planRef") String planRef,
        @JsonProperty("outcome") String outcome,
        @JsonProperty("tenancyId") String tenancyId,
        @JsonProperty("callerRef") String callerRef,
        @JsonProperty("assigneeId") String assigneeId,
        @JsonProperty("resolution") String resolution,
        @JsonProperty("candidateGroups") String candidateGroups) {

    /** Convert from a live lifecycle event for publishing to the PostgreSQL channel. */
    static WorkItemEventPayload from(final WorkItemLifecycleEvent event) {
        return new WorkItemEventPayload(
                event.type(), event.sourceUri(), event.subject(),
                event.workItemId(), event.status(), event.occurredAt(),
                event.actor(), event.detail(), event.rationale(), event.planRef(),
                event.outcome(), event.tenancyId(),
                event.callerRef(), event.assigneeId(), event.resolution(), event.candidateGroups());
    }

    /** Reconstruct a lifecycle event from a received wire payload. */
    WorkItemLifecycleEvent toEvent() {
        return WorkItemLifecycleEvent.fromWire(type, source, subject,
                workItemId, status, occurredAt, actor, detail, rationale, planRef, outcome, tenancyId,
                callerRef, assigneeId, resolution, candidateGroups);
    }
}
