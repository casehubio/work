# io.casehub.work.api.BreachType

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Identifies which SLA deadline a WorkItem breached.

## Enum Constants

### `CLAIM_EXPIRED` (`io.casehub.work.api.BreachType`)

Nobody claimed the WorkItem within the pool deadline.

### `COMPLETION_EXPIRED` (`io.casehub.work.api.BreachType`)

The WorkItem was claimed but not completed within the completion deadline.

## Constructors

### `private BreachType()`

## Methods

### `public static io.casehub.work.api.BreachType valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.BreachType[] values()`
