package io.casehub.work.runtime.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.work.api.WorkItemGroupLifecycleEvent;

/**
 * Fires WorkItemGroupLifecycleEvent on both sync and async CDI channels.
 *
 * The same event instance is passed to both fire() and fireAsync().
 * WorkItemGroupLifecycleEvent is immutable (all final fields) — this is safe.
 * Async observer failures are logged but do not propagate.
 */
@ApplicationScoped
public class WorkItemGroupLifecycleEmitter {

    private static final Logger LOG = Logger.getLogger(WorkItemGroupLifecycleEmitter.class);

    @Inject
    Event<WorkItemGroupLifecycleEvent> delegate;

    public void emit(final WorkItemGroupLifecycleEvent event) {
        delegate.fire(event);
        delegate.fireAsync(event)
                .exceptionally(ex -> {
                    LOG.warnf(ex, "Async group lifecycle observer failure for group %s", event.groupId());
                    return null;
                });
    }
}
