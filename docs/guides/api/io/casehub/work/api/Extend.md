# io.casehub.work.api.BreachDecision.Extend

**Package:** `io.casehub.work.api`

**Kind:** `record`

Extends the active deadline by `by` without changing WorkItem status.
For `BreachType.COMPLETION_EXPIRED`, pushes `expiresAt` forward.
For `BreachType.CLAIM_EXPIRED`, pushes `claimDeadline` forward.

## Fields

### `by` (`java.time.Duration`)

## Record Components

### `by` (`java.time.Duration`)

## Constructors

### `public Extend(java.time.Duration by)`

#### Parameters

- `by` (`java.time.Duration`)

## Methods

### `public java.time.Duration by()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`
