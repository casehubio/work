package io.casehub.work.runtime.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.casehub.work.api.WorkEventType;
import io.casehub.work.api.WorkLifecycleEvent;
import io.casehub.work.api.WorkItemEvent;
import io.casehub.work.api.WorkItemRef;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemStatus;

/**
 * CDI event fired on every WorkItem lifecycle transition.
 * Extends {@link WorkLifecycleEvent} so quarkus-work-core observers (e.g. FilterRegistryEngine)
 * receive it automatically.
 *
 * <p>
 * The WorkItem entity is embedded so observers can access current state without
 * a separate store lookup. All existing accessor methods are preserved.
 *
 * <h2>Firing contract — fires AFTER the mutation is persisted</h2>
 * <p>
 * This event is fired by {@link io.casehub.work.runtime.service.WorkItemService}
 * <em>after</em> the WorkItem has been mutated and written to the store via
 * {@code workItemStore.put(workItem)}. By the time any observer receives this event,
 * the WorkItem's new state is already the current state in the store.
 *
 * <p>
 * <strong>This has a critical consequence for observers that need the pre-mutation state.</strong>
 * If an observer calls {@code workItemStore.get(event.workItemId())} inside its handler,
 * it receives the <em>post</em>-mutation entity — the "before" is gone. Observers that
 * must compare before and after (for example, to detect which queues a WorkItem entered
 * or left) must maintain their own record of the previous state between events.
 *
 * <p>
 * The {@code status} field in this event records the status <em>after</em> the transition.
 * No "previous status" field is provided in the event itself.
 */
public final class WorkItemLifecycleEvent extends WorkLifecycleEvent implements WorkItemEvent {

    private final String type;
    private final String sourceUri;
    private final String subject;
    private final UUID workItemId;
    private final WorkItemStatus status;
    private final Instant occurredAt;
    private final String actor;
    private final String detail;
    private final String rationale;
    private final String planRef;
    private final String outcome;
    private final String tenancyId;
    private final String callerRef;
    private final String assigneeId;
    private final String resolution;
    private final String candidateGroups;
    private final WorkItem workItem;

    private WorkItemLifecycleEvent(final String type, final String sourceUri, final String subject,
            final UUID workItemId, final WorkItemStatus status, final Instant occurredAt,
            final String actor, final String detail, final String rationale, final String planRef,
            final String outcome, final String tenancyId,
            final String callerRef, final String assigneeId, final String resolution, final String candidateGroups,
            final WorkItem workItem) {
        this.type = type;
        this.sourceUri = sourceUri;
        this.subject = subject;
        this.workItemId = workItemId;
        this.status = status;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.detail = detail;
        this.rationale = rationale;
        this.planRef = planRef;
        this.outcome = outcome;
        this.tenancyId = tenancyId;
        this.callerRef = callerRef;
        this.assigneeId = assigneeId;
        this.resolution = resolution;
        this.candidateGroups = candidateGroups;
        this.workItem = workItem;
    }

    /**
     * Creates a lifecycle event with the standard WorkItems type prefix.
     *
     * @param eventName the audit event name (e.g. "CREATED") — lowercased automatically
     * @param workItem the WorkItem entity in its post-mutation state
     * @param actor who triggered the transition
     * @param detail optional JSON detail (nullable)
     */
    public static WorkItemLifecycleEvent of(final String eventName, final WorkItem workItem,
            final String actor, final String detail) {
        return new WorkItemLifecycleEvent(
                "io.casehub.work.workitem." + eventName.toLowerCase(),
                "/workitems/" + workItem.id,
                workItem.id.toString(),
                workItem.id, workItem.status, Instant.now(),
                actor, detail, null, null, workItem.outcome, workItem.tenancyId,
                workItem.callerRef, workItem.assigneeId, workItem.resolution, workItem.candidateGroups,
                workItem);
    }

    /**
     * Creates a lifecycle event with rationale and plan reference.
     * Used when the actor's stated basis and governing policy are known.
     *
     * @param eventName the audit event name
     * @param workItem the WorkItem entity in its post-mutation state
     * @param actor who triggered the transition
     * @param detail optional JSON detail (nullable)
     * @param rationale the actor's stated basis for the decision (nullable)
     * @param planRef the policy/procedure version that governed this action (nullable)
     */
    public static WorkItemLifecycleEvent of(final String eventName, final WorkItem workItem,
            final String actor, final String detail,
            final String rationale, final String planRef) {
        return new WorkItemLifecycleEvent(
                "io.casehub.work.workitem." + eventName.toLowerCase(),
                "/workitems/" + workItem.id,
                workItem.id.toString(),
                workItem.id, workItem.status, Instant.now(),
                actor, detail, rationale, planRef, workItem.outcome, workItem.tenancyId,
                workItem.callerRef, workItem.assigneeId, workItem.resolution, workItem.candidateGroups,
                workItem);
    }

    /**
     * Reconstructs a lifecycle event from wire-format fields — for use by distributed
     * broadcaster implementations that receive serialised events from other nodes.
     *
     * <p>
     * The {@code workItem} entity is {@code null} on the receiving node. This is intentional:
     * the SSE endpoint serialises only the scalar fields (workItem is {@code @JsonIgnore}),
     * so SSE clients receive identical output regardless of whether the event originated
     * locally or was reconstructed from the wire. Callers must not invoke {@link #source()}
     * or {@link #context()} on wire-reconstructed events.
     */
    public static WorkItemLifecycleEvent fromWire(final String type, final String sourceUri,
            final String subject, final UUID workItemId, final WorkItemStatus status,
            final Instant occurredAt, final String actor, final String detail,
            final String rationale, final String planRef, final String outcome, final String tenancyId,
            final String callerRef, final String assigneeId, final String resolution, final String candidateGroups) {
        return new WorkItemLifecycleEvent(type, sourceUri, subject, workItemId, status,
                occurredAt, actor, detail, rationale, planRef, outcome, tenancyId,
                callerRef, assigneeId, resolution, candidateGroups, null);
    }

    // ---- Existing accessors preserved (same names as old record components) ----

    /** The CloudEvents type string (e.g. "io.casehub.work.workitem.created"). */
    @JsonProperty("type")
    public String type() {
        return type;
    }

    /**
     * The CloudEvents source URI (e.g. "/workitems/{id}").
     * Use {@link #source()} for the WorkItem entity itself.
     */
    @JsonProperty("source")
    public String sourceUri() {
        return sourceUri;
    }

    /** The CloudEvents subject — the WorkItem UUID as a string. */
    @JsonProperty("subject")
    public String subject() {
        return subject;
    }

    /** The affected WorkItem's UUID. */
    @JsonProperty("workItemId")
    public UUID workItemId() {
        return workItemId;
    }

    /** The status AFTER the transition. */
    @JsonProperty("status")
    public WorkItemStatus status() {
        return status;
    }

    /** When this event was created. */
    @JsonProperty("occurredAt")
    public Instant occurredAt() {
        return occurredAt;
    }

    /** Who triggered the transition. */
    @JsonProperty("actor")
    public String actor() {
        return actor;
    }

    /** Optional detail payload (e.g. resolution text, rejection reason). */
    @JsonProperty("detail")
    public String detail() {
        return detail;
    }

    /** The actor's stated basis for the decision (nullable). */
    @JsonProperty("rationale")
    public String rationale() {
        return rationale;
    }

    /** The policy/procedure version that governed this action (nullable). */
    @JsonProperty("planRef")
    public String planRef() {
        return planRef;
    }

    /**
     * The named outcome recorded at completion (e.g. {@code "approved"}, {@code "rejected"}).
     *
     * <p>
     * Null in two distinct cases:
     * <ol>
     * <li>Non-completion events (CREATED, ASSIGNED, etc.) — no outcome is applicable.</li>
     * <li>System-initiated completions via {@code completeFromSystem()} (e.g. multi-instance
     *     threshold reached by {@code MultiInstanceGroupPolicy}) — no human-assigned outcome.</li>
     * </ol>
     * Observers that switch on outcome must handle null explicitly.
     */
    @JsonProperty("outcome")
    public String outcome() {
        return outcome;
    }

    /**
     * The tenancy ID of the WorkItem this event belongs to.
     * Server-side only — never serialised to SSE clients.
     */
    @JsonIgnore
    public String tenancyId() {
        return tenancyId;
    }

    /**
     * The callerRef from the WorkItem (external correlation identifier).
     * For wire-reconstructed events, this is stored independently; for local events,
     * it is read from the embedded workItem entity.
     */
    @JsonProperty("callerRef")
    public String callerRef() {
        return callerRef;
    }

    /**
     * The assigneeId from the WorkItem (who is assigned to complete this work).
     * For wire-reconstructed events, this is stored independently; for local events,
     * it is read from the embedded workItem entity.
     */
    @JsonProperty("assigneeId")
    public String assigneeId() {
        return assigneeId;
    }

    /**
     * The resolution JSON from the WorkItem.
     * For wire-reconstructed events, this is stored independently; for local events,
     * it is read from the embedded workItem entity.
     */
    @JsonProperty("resolution")
    public String resolution() {
        return resolution;
    }

    /**
     * The candidateGroups from the WorkItem (comma-separated list of eligible groups).
     * For wire-reconstructed events, this is stored independently; for local events,
     * it is read from the embedded workItem entity.
     */
    @JsonProperty("candidateGroups")
    public String candidateGroups() {
        return candidateGroups;
    }

    // ---- WorkItemEvent interface implementation ----

    /**
     * Returns a {@link WorkItemRef} built from this event's data.
     * For local events (workItem != null), fields are read from the embedded entity.
     * For wire events (workItem == null), fields are read from independently stored values.
     */
    @JsonIgnore
    @Override
    public WorkItemRef ref() {
        if (workItem != null) {
            return new WorkItemRef(workItemId, status, workItem.callerRef, workItem.assigneeId,
                    workItem.resolution, workItem.candidateGroups, outcome, tenancyId);
        }
        return new WorkItemRef(workItemId, status, callerRef, assigneeId,
                resolution, candidateGroups, outcome, tenancyId);
    }

    // ---- WorkLifecycleEvent abstract method implementations ----

    @JsonIgnore
    @Override
    public WorkEventType eventType() {
        final String name = type.substring(type.lastIndexOf('.') + 1).toUpperCase();
        return WorkEventType.valueOf(name);
    }

    @JsonIgnore
    @Override
    public Map<String, Object> context() {
        return WorkItemContextBuilder.toMap(workItem);
    }

    /**
     * Returns the WorkItem entity in its post-mutation state.
     * Callers needing the CloudEvents source URI should use {@link #sourceUri()} instead.
     */
    @JsonIgnore
    @Override
    public Object source() {
        return workItem;
    }
}
