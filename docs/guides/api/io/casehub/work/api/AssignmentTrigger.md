# io.casehub.work.api.AssignmentTrigger

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Lifecycle events that trigger worker (re-)selection.

## Enum Constants

### `CREATED` (`io.casehub.work.api.AssignmentTrigger`)

WorkItem first created and persisted.

### `DELEGATED` (`io.casehub.work.api.AssignmentTrigger`)

WorkItem delegated to a different pool or individual.

### `DELEGATION_DECLINED` (`io.casehub.work.api.AssignmentTrigger`)

WorkItem returned to pool after a delegatee declined a targeted delegation.

### `RELEASED` (`io.casehub.work.api.AssignmentTrigger`)

WorkItem returned to pool by its assignee (release).

### `SLA_ESCALATED` (`io.casehub.work.api.AssignmentTrigger`)

WorkItem returned to an escalation pool after an SLA breach.

## Constructors

### `private AssignmentTrigger()`

## Methods

### `public static io.casehub.work.api.AssignmentTrigger valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.AssignmentTrigger[] values()`
