# io.casehub.work.api.WorkItemSummary

**Package:** `io.casehub.work.api`

**Kind:** `record`

## Fields

### `byPriority` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `byStatus` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `claimDeadlineBreached` (`long`)

### `oldestCreatedAt` (`java.time.Instant`)

### `overdue` (`long`)

### `total` (`long`)

## Record Components

### `byPriority` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `byStatus` (`java.util.Map<java.lang.String,java.lang.Long>`)

### `claimDeadlineBreached` (`long`)

### `oldestCreatedAt` (`java.time.Instant`)

### `overdue` (`long`)

### `total` (`long`)

## Constructors

### `public WorkItemSummary(long total, java.util.Map<java.lang.String,java.lang.Long> byStatus, java.util.Map<java.lang.String,java.lang.Long> byPriority, long overdue, long claimDeadlineBreached, java.time.Instant oldestCreatedAt)`

#### Parameters

- `total` (`long`)
- `byStatus` (`java.util.Map<java.lang.String,java.lang.Long>`)
- `byPriority` (`java.util.Map<java.lang.String,java.lang.Long>`)
- `overdue` (`long`)
- `claimDeadlineBreached` (`long`)
- `oldestCreatedAt` (`java.time.Instant`)

## Methods

### `public java.util.Map<java.lang.String,java.lang.Long> byPriority()`

### `public java.util.Map<java.lang.String,java.lang.Long> byStatus()`

### `public long claimDeadlineBreached()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.time.Instant oldestCreatedAt()`

### `public long overdue()`

### `public final java.lang.String toString()`

### `public long total()`
