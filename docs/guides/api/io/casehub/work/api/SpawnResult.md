# io.casehub.work.api.SpawnResult

**Package:** `io.casehub.work.api`

**Kind:** `record`

Result of a spawn operation.

## Fields

### `children` (`java.util.List<io.casehub.work.api.SpawnedChild>`)

### `created` (`boolean`)

### `groupId` (`java.util.UUID`)

## Record Components

### `children` (`java.util.List<io.casehub.work.api.SpawnedChild>`)

one entry per child created, in the same order as the request

### `created` (`boolean`)

true if a new group was created; false if the idempotencyKey matched
       an existing group (the existing group is returned, no children created)

### `groupId` (`java.util.UUID`)

UUID of the `WorkItemSpawnGroup` tracking this spawn batch

## Constructors

### `public SpawnResult(java.util.UUID groupId, java.util.List<io.casehub.work.api.SpawnedChild> children, boolean created)`

#### Parameters

- `groupId` (`java.util.UUID`)
- `children` (`java.util.List<io.casehub.work.api.SpawnedChild>`)
- `created` (`boolean`)

## Methods

### `public java.util.List<io.casehub.work.api.SpawnedChild> children()`

### `public boolean created()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.UUID groupId()`

### `public final int hashCode()`

### `public final java.lang.String toString()`
