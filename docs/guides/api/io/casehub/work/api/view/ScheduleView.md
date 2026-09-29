# io.casehub.work.api.view.ScheduleView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `active` (`boolean`)

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `cronExpression` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `lastFiredAt` (`java.time.Instant`)

### `name` (`java.lang.String`)

### `nextFireAt` (`java.time.Instant`)

### `templateId` (`java.util.UUID`)

## Record Components

### `active` (`boolean`)

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `cronExpression` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `lastFiredAt` (`java.time.Instant`)

### `name` (`java.lang.String`)

### `nextFireAt` (`java.time.Instant`)

### `templateId` (`java.util.UUID`)

## Constructors

### `public ScheduleView(java.util.UUID id, java.lang.String name, java.util.UUID templateId, java.lang.String cronExpression, boolean active, java.lang.String createdBy, java.time.Instant createdAt, java.time.Instant lastFiredAt, java.time.Instant nextFireAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `name` (`java.lang.String`)
- `templateId` (`java.util.UUID`)
- `cronExpression` (`java.lang.String`)
- `active` (`boolean`)
- `createdBy` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `lastFiredAt` (`java.time.Instant`)
- `nextFireAt` (`java.time.Instant`)

## Methods

### `public boolean active()`

### `public java.time.Instant createdAt()`

### `public java.lang.String createdBy()`

### `public java.lang.String cronExpression()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.time.Instant lastFiredAt()`

### `public java.lang.String name()`

### `public java.time.Instant nextFireAt()`

### `public java.util.UUID templateId()`

### `public final java.lang.String toString()`
