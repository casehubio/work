# io.casehub.work.api.BreachDecision.Exhausted

**Package:** `io.casehub.work.api`

**Kind:** `record`

All configured SLA breach policy branches have been exhausted.
The WorkItem transitions to `WorkItemStatus.ESCALATED`
(terminal) and requires operator intervention to resolve.

<p>Returned by the runtime when a `Chained` policy's primary and fallback both
throw `BreachExecutionFailed`. May also be returned directly by a
`SlaBreachPolicy` implementation when it determines no resolution is possible.

## Fields

### `reason` (`java.lang.String`)

## Record Components

### `reason` (`java.lang.String`)

## Constructors

### `public Exhausted(java.lang.String reason)`

#### Parameters

- `reason` (`java.lang.String`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String reason()`

### `public final java.lang.String toString()`
