# io.casehub.work.api.view.ActorReportView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `actorId` (`java.lang.String`)

### `avgCompletionMinutes` (`java.lang.Double`)

### `byType` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `totalAssigned` (`long`)

### `totalCompleted` (`long`)

### `totalRejected` (`long`)

## Record Components

### `actorId` (`java.lang.String`)

### `avgCompletionMinutes` (`java.lang.Double`)

### `byType` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `totalAssigned` (`long`)

### `totalCompleted` (`long`)

### `totalRejected` (`long`)

## Constructors

### `public ActorReportView(java.lang.String actorId, long totalAssigned, long totalCompleted, long totalRejected, java.lang.Double avgCompletionMinutes, java.util.Map<java.lang.String,java.lang.Long> byType)`

#### Parameters

- `actorId` (`java.lang.String`)
- `totalAssigned` (`long`)
- `totalCompleted` (`long`)
- `totalRejected` (`long`)
- `avgCompletionMinutes` (`java.lang.Double`)
- `byType` (`java.util.Map<java.lang.String,java.lang.Long>`)

## Methods

### `public java.lang.String actorId()`

### `public java.lang.Double avgCompletionMinutes()`

### `public java.util.Map<java.lang.String,java.lang.Long> byType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`

### `public long totalAssigned()`

### `public long totalCompleted()`

### `public long totalRejected()`
