# io.casehub.work.api.WorkerCandidate

**Package:** `io.casehub.work.api`

**Kind:** `record`

A potential assignee for a WorkItem.

<p>The `activeWorkItemCount` is pre-populated by
`WorkItemAssignmentService` before the candidate list is passed to
`WorkerSelectionStrategy.select`. A count of 0 means either zero
active items or that the registry did not provide workload data.

<p>CaseHub alignment: corresponds to `HumanWorkerProfile`.

## Fields

### `activeWorkItemCount` (`int`)

### `capabilities` (`java.util.Set<io.casehub.work.api.Capability>`)

### `id` (`java.lang.String`)

## Record Components

### `activeWorkItemCount` (`int`)

### `capabilities` (`java.util.Set<io.casehub.work.api.Capability>`)

### `id` (`java.lang.String`)

## Constructors

### `public WorkerCandidate(java.lang.String id, java.util.Set<io.casehub.work.api.Capability> capabilities, int activeWorkItemCount)`

#### Parameters

- `id` (`java.lang.String`)
- `capabilities` (`java.util.Set<io.casehub.work.api.Capability>`)
- `activeWorkItemCount` (`int`)

## Methods

### `public int activeWorkItemCount()`

### `public java.util.Set<io.casehub.work.api.Capability> capabilities()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String id()`

### `public static io.casehub.work.api.WorkerCandidate of(java.lang.String id)`

Convenience factory — capabilities empty, workload 0.

#### Parameters

- `id` (`java.lang.String`)

### `public final java.lang.String toString()`

### `public io.casehub.work.api.WorkerCandidate withActiveWorkItemCount(int count)`

Returns a new candidate with the updated active WorkItem count.

#### Parameters

- `count` (`int`)
