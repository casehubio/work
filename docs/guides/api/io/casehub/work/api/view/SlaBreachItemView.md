# io.casehub.work.api.view.SlaBreachItemView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `breachDurationMinutes` (`long`)

### `completedAt` (`java.time.Instant`)

### `expiresAt` (`java.time.Instant`)

### `priority` (`java.lang.String`)

### `status` (`java.lang.String`)

### `types` (`java.lang.String`)

### `workItemId` (`java.lang.String`)

## Record Components

### `breachDurationMinutes` (`long`)

### `completedAt` (`java.time.Instant`)

### `expiresAt` (`java.time.Instant`)

### `priority` (`java.lang.String`)

### `status` (`java.lang.String`)

### `types` (`java.lang.String`)

### `workItemId` (`java.lang.String`)

## Constructors

### `public SlaBreachItemView(java.lang.String workItemId, java.lang.String types, java.lang.String priority, java.time.Instant expiresAt, java.time.Instant completedAt, java.lang.String status, long breachDurationMinutes)`

#### Parameters

- `workItemId` (`java.lang.String`)
- `types` (`java.lang.String`)
- `priority` (`java.lang.String`)
- `expiresAt` (`java.time.Instant`)
- `completedAt` (`java.time.Instant`)
- `status` (`java.lang.String`)
- `breachDurationMinutes` (`long`)

## Methods

### `public long breachDurationMinutes()`

### `public java.time.Instant completedAt()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.time.Instant expiresAt()`

### `public final int hashCode()`

### `public java.lang.String priority()`

### `public java.lang.String status()`

### `public final java.lang.String toString()`

### `public java.lang.String types()`

### `public java.lang.String workItemId()`
