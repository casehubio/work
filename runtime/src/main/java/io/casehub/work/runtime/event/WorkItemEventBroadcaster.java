package io.casehub.work.runtime.event;

import java.util.UUID;

import io.smallrye.mutiny.Multi;

/**
 * SPI: fan-out WorkItem lifecycle events to SSE subscribers.
 *
 * <p>
 * The default implementation ({@link LocalWorkItemEventBroadcaster}) uses an in-process
 * Mutiny {@code BroadcastProcessor}. Alternative backends (Redis pub/sub, PostgreSQL
 * LISTEN/NOTIFY, Vert.x EventBus) can replace it via {@code @Alternative @Priority(1)}.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Implementations must return a hot stream — past events are not replayed.
 *   <li>Filters ({@code workItemId}, {@code type}, {@code tenancyId}) are applied per subscriber, not globally.
 *   <li>The {@code tenancyId} filter is mandatory — never null. This ensures tenant isolation.
 *   <li>Other filters being null delivers all events for the tenant.
 * </ul>
 */
public interface WorkItemEventBroadcaster {

    /**
     * Returns a hot stream of lifecycle events, filtered by tenant and optionally by workItemId/type.
     *
     * @param workItemId if non-null, only events for this WorkItem are emitted
     * @param type if non-null, only events whose type suffix matches (case-insensitive) are emitted
     * @param tenancyId tenant scope — required, never null; only events for this tenant are emitted
     * @return hot {@link Multi} of matching {@link WorkItemLifecycleEvent} instances
     */
    Multi<WorkItemLifecycleEvent> stream(UUID workItemId, String type, String tenancyId);
}
