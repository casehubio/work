# io.casehub.work.api.GroupStatus

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Aggregate completion status of a multi-instance WorkItem group.

## Enum Constants

### `COMPLETED` (`io.casehub.work.api.GroupStatus`)

Threshold reached with majority approval — group completed successfully.

### `IN_PROGRESS` (`io.casehub.work.api.GroupStatus`)

Group is still accepting completions — threshold not yet reached.

### `REJECTED` (`io.casehub.work.api.GroupStatus`)

Threshold reached but with majority rejection or escalation — group rejected.

## Constructors

### `private GroupStatus()`

## Methods

### `public boolean isActive()`

### `public boolean isTerminal()`

### `public static io.casehub.work.api.GroupStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.GroupStatus[] values()`
