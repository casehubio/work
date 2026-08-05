# io.casehub.work.api.ClaimSlaContext

**Package:** `io.casehub.work.api`

**Kind:** `record`

Context passed to `ClaimSlaPolicy.computePoolDeadline` when a WorkItem
returns to the unclaimed pool.

## Fields

### `accumulatedUnclaimedDuration` (`java.time.Duration`)

### `now` (`java.time.Instant`)

### `submittedAt` (`java.time.Instant`)

### `totalPoolSlaDuration` (`java.time.Duration`)

## Record Components

### `accumulatedUnclaimedDuration` (`java.time.Duration`)

total time the item has already spent unclaimed
       across all previous unclaimed phases

### `now` (`java.time.Instant`)

current instant (passed in for testability)

### `submittedAt` (`java.time.Instant`)

when the WorkItem was first created

### `totalPoolSlaDuration` (`java.time.Duration`)

the configured pool SLA (e.g. Duration.ofHours(2))

## Constructors

### `public ClaimSlaContext(java.time.Instant submittedAt, java.time.Duration totalPoolSlaDuration, java.time.Duration accumulatedUnclaimedDuration, java.time.Instant now)`

#### Parameters

- `submittedAt` (`java.time.Instant`)
- `totalPoolSlaDuration` (`java.time.Duration`)
- `accumulatedUnclaimedDuration` (`java.time.Duration`)
- `now` (`java.time.Instant`)

## Methods

### `public java.time.Duration accumulatedUnclaimedDuration()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.time.Instant now()`

### `public java.time.Instant submittedAt()`

### `public final java.lang.String toString()`

### `public java.time.Duration totalPoolSlaDuration()`
