# io.casehub.work.api.view.QueueHealthReportView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `avgPendingAgeSeconds` (`long`)

### `criticalOverdueCount` (`long`)

### `oldestUnclaimedCreatedAt` (`java.time.Instant`)

### `overdueCount` (`long`)

### `pendingCount` (`long`)

### `timestamp` (`java.time.Instant`)

## Record Components

### `avgPendingAgeSeconds` (`long`)

### `criticalOverdueCount` (`long`)

### `oldestUnclaimedCreatedAt` (`java.time.Instant`)

### `overdueCount` (`long`)

### `pendingCount` (`long`)

### `timestamp` (`java.time.Instant`)

## Constructors

### `public QueueHealthReportView(java.time.Instant timestamp, long overdueCount, long pendingCount, long avgPendingAgeSeconds, java.time.Instant oldestUnclaimedCreatedAt, long criticalOverdueCount)`

#### Parameters

- `timestamp` (`java.time.Instant`)
- `overdueCount` (`long`)
- `pendingCount` (`long`)
- `avgPendingAgeSeconds` (`long`)
- `oldestUnclaimedCreatedAt` (`java.time.Instant`)
- `criticalOverdueCount` (`long`)

## Methods

### `public long avgPendingAgeSeconds()`

### `public long criticalOverdueCount()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.time.Instant oldestUnclaimedCreatedAt()`

### `public long overdueCount()`

### `public long pendingCount()`

### `public java.time.Instant timestamp()`

### `public final java.lang.String toString()`
