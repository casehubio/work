# io.casehub.work.api.spi.WorkItemObserver

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for observing WorkItem lifecycle transitions.

<p>Implementations are discovered via CDI and notified on every status change.
Multiple observers may be registered; execution order is undefined.
Observers run synchronously in the emitter's transaction context.

<p>To avoid circular dependencies, this interface lives in `casehub-work-api`
and accepts a `WorkItemStatusEvent` (a subset of the runtime's
WorkItemLifecycleEvent, carrying only the fields available in the api module).

## Methods

### `public abstract void onStatusChange(io.casehub.work.api.WorkItemStatusEvent event)`

#### Parameters

- `event` (`io.casehub.work.api.WorkItemStatusEvent`)
