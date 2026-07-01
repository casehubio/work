package io.casehub.work.api.spi;

import io.casehub.work.api.WorkItemStatusEvent;

/**
 * SPI for observing WorkItem lifecycle transitions.
 *
 * <p>Implementations are discovered via CDI and notified on every status change.
 * Multiple observers may be registered; execution order is undefined.
 * Observers run synchronously in the emitter's transaction context.
 *
 * <p>To avoid circular dependencies, this interface lives in {@code casehub-work-api}
 * and accepts a {@link WorkItemStatusEvent} (a subset of the runtime's
 * WorkItemLifecycleEvent, carrying only the fields available in the api module).
 */
public interface WorkItemObserver {

    void onStatusChange(WorkItemStatusEvent event);
}
