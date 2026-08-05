# io.casehub.work.api.SpawnedChild

**Package:** `io.casehub.work.api`

**Kind:** `record`

Result for a single spawned child.

## Fields

### `callerRef` (`java.lang.String`)

### `workItemId` (`java.util.UUID`)

## Record Components

### `callerRef` (`java.lang.String`)

echoed from `ChildSpec.callerRef()` — confirms the
       association between the caller's routing key and the new WorkItem

### `workItemId` (`java.util.UUID`)

the UUID of the created child WorkItem

## Constructors

### `public SpawnedChild(java.util.UUID workItemId, java.lang.String callerRef)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `callerRef` (`java.lang.String`)

## Methods

### `public java.lang.String callerRef()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`

### `public java.util.UUID workItemId()`
