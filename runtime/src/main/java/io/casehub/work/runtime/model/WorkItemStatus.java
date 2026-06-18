package io.casehub.work.runtime.model;

/**
 * Lifecycle status of a {@link WorkItem}.
 */
public enum WorkItemStatus {

    /** WorkItem has been created but not yet assigned to anyone. */
    PENDING,

    /** WorkItem has been assigned to a specific assignee but work has not started. */
    ASSIGNED,

    /** WorkItem is actively being worked on by the assignee. */
    IN_PROGRESS,

    /** WorkItem has been completed successfully. */
    COMPLETED,

    /** WorkItem was rejected by the assignee or a reviewer. */
    REJECTED,

    /** System or infrastructure failure — distinct from {@link #REJECTED} (actor's deliberate decision).
     *  FAULTED means the system hosting or processing this WorkItem failed.
     *  PENDING→FAULTED means infrastructure failed before anyone could act. */
    FAULTED,

    /** WorkItem has been forwarded to a named actor for acceptance — pre-acceptance hold.
     *  Non-terminal: the named actor must call {@code acceptDelegation()} or {@code declineDelegation()}.
     *  <p><strong>Cross-system semantics differ:</strong>
     *  {@code CommitmentState.DELEGATED} (casehub-qhorus) is <em>terminal</em> — obligation transferred
     *  and discharged. {@code PlanItemStatus.DELEGATED} (casehub-engine) means control passed to an
     *  external actor (broader). See {@code docs/LIFECYCLE.md} for cross-system DELEGATED semantics. */
    DELEGATED,

    /** WorkItem has been temporarily suspended and is awaiting resumption. */
    SUSPENDED,

    /** WorkItem was cancelled before completion. */
    CANCELLED,

    /** WorkItem's deadline passed without resolution. */
    EXPIRED,

    /** WorkItem was escalated due to expiry or policy breach. */
    ESCALATED,

    /** WorkItem superseded by context change — the case context changed, making this work irrelevant.
     *  Distinct from {@link #CANCELLED} (deliberate stop by a human or system).
     *  Typically triggered by the engine or an orchestrator, not by the actor. */
    OBSOLETE;

    /**
     * Returns {@code true} if this status represents a terminal (end) state from
     * which the WorkItem cannot transition further under normal lifecycle rules.
     * Terminal statuses are: {@link #COMPLETED}, {@link #REJECTED}, {@link #FAULTED},
     * {@link #CANCELLED}, {@link #OBSOLETE}, {@link #EXPIRED}, and {@link #ESCALATED}.
     *
     * @return {@code true} when the WorkItem has reached a terminal state
     */
    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, REJECTED, FAULTED, CANCELLED, OBSOLETE, EXPIRED, ESCALATED -> true;
            default -> false;
        };
    }

    /**
     * Returns {@code true} if this status represents an active (non-terminal, non-expired)
     * state in which the WorkItem is still being processed.
     * Active statuses are: {@link #PENDING}, {@link #ASSIGNED},
     * {@link #IN_PROGRESS}, and {@link #SUSPENDED}.
     *
     * @return {@code true} when the WorkItem is still actively in progress
     */
    public boolean isActive() {
        return switch (this) {
            case PENDING, ASSIGNED, IN_PROGRESS, SUSPENDED, DELEGATED -> true;
            default -> false;
        };
    }
}
