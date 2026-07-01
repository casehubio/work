package io.casehub.work.api.spi;

import java.util.Optional;

import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemRef;

public interface WorkItemCreator {

    WorkItemRef create(WorkItemCreateRequest request);

    /**
     * Find a WorkItem by its caller reference.
     *
     * <p>When multiple WorkItems share the same {@code callerRef} (e.g. first expires,
     * second created and completed), returns the <strong>most recently created</strong>
     * match. Callers should not assume callerRef uniqueness across the lifecycle.
     *
     * @param callerRef the caller reference to look up; must not be {@code null}
     * @return the most recently created WorkItem with the given callerRef, or empty
     */
    Optional<WorkItemRef> findByCallerRef(String callerRef);

    /**
     * Find a non-terminal (active) WorkItem by its caller reference.
     *
     * <p>When multiple active WorkItems share the same {@code callerRef}, returns the
     * <strong>most recently created</strong> match. Terminal WorkItems are excluded.
     *
     * @param callerRef the caller reference to look up; must not be {@code null}
     * @return the most recently created active WorkItem with the given callerRef, or empty
     */
    Optional<WorkItemRef> findActiveByCallerRef(String callerRef);

    /**
     * Mark the most recent WorkItem with the given callerRef as OBSOLETE.
     *
     * <p>Idempotent: if the WorkItem is already in a terminal state, this is a no-op.
     * If no WorkItem matches the callerRef, this is a no-op.
     *
     * @param callerRef the caller reference identifying the WorkItem to obsolete
     */
    void obsoleteByCallerRef(String callerRef);
}
