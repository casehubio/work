# io.casehub.work.api.view.EscalationSummaryView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `eventType` (`java.lang.String`)

### `generatedAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `summary` (`java.lang.String`)

### `workItemId` (`java.util.UUID`)

## Record Components

### `eventType` (`java.lang.String`)

### `generatedAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `summary` (`java.lang.String`)

### `workItemId` (`java.util.UUID`)

## Constructors

### `public EscalationSummaryView(java.util.UUID id, java.util.UUID workItemId, java.lang.String eventType, java.lang.String summary, java.time.Instant generatedAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `workItemId` (`java.util.UUID`)
- `eventType` (`java.lang.String`)
- `summary` (`java.lang.String`)
- `generatedAt` (`java.time.Instant`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String eventType()`

### `public java.time.Instant generatedAt()`

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String summary()`

### `public final java.lang.String toString()`

### `public java.util.UUID workItemId()`
