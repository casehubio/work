# io.casehub.work.api.WorkItemStatus

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Lifecycle status of a WorkItem.

## Fields

### `TERMINAL_STATUSES` (`java.util.List<io.casehub.work.api.WorkItemStatus>`)

All terminal statuses, derived from `.isTerminal()`. Single source of truth for queries.

## Enum Constants

### `ASSIGNED` (`io.casehub.work.api.WorkItemStatus`)

WorkItem has been assigned to a specific assignee but work has not started.

### `CANCELLED` (`io.casehub.work.api.WorkItemStatus`)

WorkItem was cancelled before completion.

### `COMPLETED` (`io.casehub.work.api.WorkItemStatus`)

WorkItem has been completed successfully.

### `DELEGATED` (`io.casehub.work.api.WorkItemStatus`)

WorkItem has been forwarded to a named actor for acceptance — pre-acceptance hold.
  Non-terminal: the named actor must call `acceptDelegation()` or `declineDelegation()`.
  <p><strong>Cross-system semantics differ:</strong>
  `CommitmentState.DELEGATED` (casehub-qhorus) is <em>terminal</em> — obligation transferred
  and discharged. `PlanItemStatus.DELEGATED` (casehub-engine) means control passed to an
  external actor (broader). See `docs/LIFECYCLE.md` for cross-system DELEGATED semantics.

### `ESCALATED` (`io.casehub.work.api.WorkItemStatus`)

WorkItem was escalated due to expiry or policy breach.

### `EXPIRED` (`io.casehub.work.api.WorkItemStatus`)

WorkItem's deadline passed without resolution.

### `FAULTED` (`io.casehub.work.api.WorkItemStatus`)

System or infrastructure failure — distinct from `.REJECTED` (actor's deliberate decision).
  FAULTED means the system hosting or processing this WorkItem failed.
  PENDING→FAULTED means infrastructure failed before anyone could act.

### `IN_PROGRESS` (`io.casehub.work.api.WorkItemStatus`)

WorkItem is actively being worked on by the assignee.

### `OBSOLETE` (`io.casehub.work.api.WorkItemStatus`)

WorkItem superseded by context change — the case context changed, making this work irrelevant.
  Distinct from `.CANCELLED` (deliberate stop by a human or system).
  Typically triggered by the engine or an orchestrator, not by the actor.

### `PENDING` (`io.casehub.work.api.WorkItemStatus`)

WorkItem has been created but not yet assigned to anyone.

### `REJECTED` (`io.casehub.work.api.WorkItemStatus`)

WorkItem was rejected by the assignee or a reviewer.

### `SUSPENDED` (`io.casehub.work.api.WorkItemStatus`)

WorkItem has been temporarily suspended and is awaiting resumption.

## Constructors

### `private WorkItemStatus()`

## Methods

### `public boolean isActive()`

Returns `true` if this status represents an active (non-terminal, non-expired)
state in which the WorkItem is still being processed.
Active statuses are: `.PENDING`, `.ASSIGNED`,
`.IN_PROGRESS`, and `.SUSPENDED`.

#### Returns

`true` when the WorkItem is still actively in progress

### `public boolean isTerminal()`

Returns `true` if this status represents a terminal (end) state from
which the WorkItem cannot transition further under normal lifecycle rules.
Terminal statuses are: `.COMPLETED`, `.REJECTED`, `.FAULTED`,
`.CANCELLED`, `.OBSOLETE`, `.EXPIRED`, and `.ESCALATED`.

#### Returns

`true` when the WorkItem has reached a terminal state

### `public static io.casehub.work.api.WorkItemStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.WorkItemStatus[] values()`
