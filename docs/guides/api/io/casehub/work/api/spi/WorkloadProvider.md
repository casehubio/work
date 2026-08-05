# io.casehub.work.api.spi.WorkloadProvider

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for querying the active work count for a worker.

<p>
Implement as `@ApplicationScoped @Alternative @Priority(1)` to provide
domain-specific workload data. quarkus-work provides `JpaWorkloadProvider`.
CaseHub provides its own implementation against its task store.

<p>
Used by `WorkItemAssignmentService` to populate
`WorkerCandidate.activeWorkItemCount()` before passing candidates to
`WorkerSelectionStrategy.select`.

## Methods

### `public abstract int getActiveWorkCount(java.lang.String workerId)`

Returns the count of active (non-terminal) work units held by the given worker.

#### Parameters

- `workerId` (`java.lang.String`) — the worker identifier

#### Returns

active work count; 0 if unknown or no active items
