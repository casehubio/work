# io.casehub.work.api.view.WorkItemRelationView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `relationType` (`java.lang.String`)

### `sourceId` (`java.util.UUID`)

### `targetId` (`java.util.UUID`)

## Record Components

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `relationType` (`java.lang.String`)

### `sourceId` (`java.util.UUID`)

### `targetId` (`java.util.UUID`)

## Constructors

### `public WorkItemRelationView(java.util.UUID id, java.util.UUID sourceId, java.util.UUID targetId, java.lang.String relationType, java.lang.String createdBy, java.time.Instant createdAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `sourceId` (`java.util.UUID`)
- `targetId` (`java.util.UUID`)
- `relationType` (`java.lang.String`)
- `createdBy` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)

## Methods

### `public java.time.Instant createdAt()`

### `public java.lang.String createdBy()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String relationType()`

### `public java.util.UUID sourceId()`

### `public java.util.UUID targetId()`

### `public final java.lang.String toString()`
