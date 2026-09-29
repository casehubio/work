# io.casehub.work.api.view.SpawnGroupSummaryView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `createdAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `idempotencyKey` (`java.lang.String`)

### `parentId` (`java.util.UUID`)

## Record Components

### `createdAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `idempotencyKey` (`java.lang.String`)

### `parentId` (`java.util.UUID`)

## Constructors

### `public SpawnGroupSummaryView(java.util.UUID id, java.util.UUID parentId, java.lang.String idempotencyKey, java.time.Instant createdAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `parentId` (`java.util.UUID`)
- `idempotencyKey` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)

## Methods

### `public java.time.Instant createdAt()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String idempotencyKey()`

### `public java.util.UUID parentId()`

### `public final java.lang.String toString()`
