package io.casehub.work.runtime.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Fires WorkItemLifecycleEvent on both sync and async CDI channels.
 *
 * The same event instance is passed to both fire() and fireAsync().
 * WorkItemLifecycleEvent is immutable (all final fields) — this is safe.
 * Async observer failures are logged but do not propagate.
 */
@ApplicationScoped
public class WorkItemLifecycleEmitter {

    private static final Logger LOG = Logger.getLogger(WorkItemLifecycleEmitter.class);

    @Inject
    Event<WorkItemLifecycleEvent> delegate;

    public void emit(final WorkItemLifecycleEvent event) {
        delegate.fire(event);
        delegate.fireAsync(event)
                .exceptionally(ex -> {
                    LOG.warnf(ex, "Async lifecycle observer failure for %s", event.type());
                    return null;
                });
    }
}
