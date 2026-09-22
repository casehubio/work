# io.casehub.work.api.view.SpawnGroupView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `children` (`java.util.List<io.casehub.work.api.view.SpawnGroupChildView>`)

### `createdAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `idempotencyKey` (`java.lang.String`)

### `parentId` (`java.util.UUID`)

## Record Components

### `children` (`java.util.List<io.casehub.work.api.view.SpawnGroupChildView>`)

### `createdAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `idempotencyKey` (`java.lang.String`)

### `parentId` (`java.util.UUID`)

## Constructors

### `public SpawnGroupView(java.util.UUID id, java.util.UUID parentId, java.lang.String idempotencyKey, java.time.Instant createdAt, java.util.List<io.casehub.work.api.view.SpawnGroupChildView> children)`

#### Parameters

- `id` (`java.util.UUID`)
- `parentId` (`java.util.UUID`)
- `idempotencyKey` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `children` (`java.util.List<io.casehub.work.api.view.SpawnGroupChildView>`)

## Methods

### `public java.util.List<io.casehub.work.api.view.SpawnGroupChildView> children()`

### `public java.time.Instant createdAt()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String idempotencyKey()`

### `public java.util.UUID parentId()`

### `public final java.lang.String toString()`
