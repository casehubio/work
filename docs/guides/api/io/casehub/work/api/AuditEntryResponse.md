# io.casehub.work.api.AuditEntryResponse

**Package:** `io.casehub.work.api`

**Kind:** `record`

## Fields

### `actor` (`java.lang.String`)

### `detail` (`java.lang.String`)

### `event` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `occurredAt` (`java.time.Instant`)

## Record Components

### `actor` (`java.lang.String`)

### `detail` (`java.lang.String`)

### `event` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `occurredAt` (`java.time.Instant`)

## Constructors

### `public AuditEntryResponse(java.util.UUID id, java.lang.String event, java.lang.String actor, java.lang.String detail, java.time.Instant occurredAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `event` (`java.lang.String`)
- `actor` (`java.lang.String`)
- `detail` (`java.lang.String`)
- `occurredAt` (`java.time.Instant`)

## Methods

### `public java.lang.String actor()`

### `public java.lang.String detail()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String event()`

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.time.Instant occurredAt()`

### `public final java.lang.String toString()`
