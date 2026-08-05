# io.casehub.work.api.BreachedTask

**Package:** `io.casehub.work.api`

**Kind:** `record`

Projection of a WorkItem that has breached its SLA deadline.
Passed to `SlaBreachPolicy.onBreach` so the policy can make
routing decisions without depending on the full WorkItem entity.

## Fields

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.util.Set<java.lang.String>`)

### `taskId` (`java.util.UUID`)

### `title` (`java.lang.String`)

## Record Components

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.util.Set<java.lang.String>`)

### `taskId` (`java.util.UUID`)

### `title` (`java.lang.String`)

## Constructors

### `public BreachedTask(java.util.UUID taskId, java.lang.String callerRef, java.lang.String title, java.util.Set<java.lang.String> candidateGroups)`

#### Parameters

- `taskId` (`java.util.UUID`)
- `callerRef` (`java.lang.String`)
- `title` (`java.lang.String`)
- `candidateGroups` (`java.util.Set<java.lang.String>`)

## Methods

### `public java.lang.String callerRef()`

### `public java.util.Set<java.lang.String> candidateGroups()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID taskId()`

### `public java.lang.String title()`

### `public final java.lang.String toString()`
