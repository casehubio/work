# io.casehub.work.api.SpawnRequest

**Package:** `io.casehub.work.api`

**Kind:** `record`

Request to spawn a group of child work units from a parent.

## Fields

### `children` (`java.util.List<io.casehub.work.api.ChildSpec>`)

### `idempotencyKey` (`java.lang.String`)

### `parentId` (`java.util.UUID`)

## Record Components

### `children` (`java.util.List<io.casehub.work.api.ChildSpec>`)

one entry per child to spawn; must not be null or empty

### `idempotencyKey` (`java.lang.String`)

caller-supplied deduplication key — a second call with the
       same key on the same parent returns the existing group (HTTP 200)
       without creating duplicate children

### `parentId` (`java.util.UUID`)

the UUID of the parent WorkItem

## Constructors

### `public SpawnRequest(java.util.UUID parentId, java.lang.String idempotencyKey, java.util.List<io.casehub.work.api.ChildSpec> children)`

#### Parameters

- `parentId` (`java.util.UUID`)
- `idempotencyKey` (`java.lang.String`)
- `children` (`java.util.List<io.casehub.work.api.ChildSpec>`)

## Methods

### `public java.util.List<io.casehub.work.api.ChildSpec> children()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String idempotencyKey()`

### `public java.util.UUID parentId()`

### `public final java.lang.String toString()`
