# io.casehub.work.api.AssignmentDecision

**Package:** `io.casehub.work.api`

**Kind:** `record`

Immutable outcome of a `WorkerSelectionStrategy.select` call.

<p>
A `null` field means "no change to this WorkItem field". Only non-null
fields are applied to the WorkItem by `WorkItemAssignmentService`.

## Fields

### `assigneeId` (`java.lang.String`)

### `candidateGroups` (`java.lang.String`)

### `candidateUsers` (`java.lang.String`)

## Record Components

### `assigneeId` (`java.lang.String`)

pre-assign to this actor; null = leave unassigned (pool open)

### `candidateGroups` (`java.lang.String`)

replace candidateGroups; null = keep as-is

### `candidateUsers` (`java.lang.String`)

replace candidateUsers; null = keep as-is

## Constructors

### `public AssignmentDecision(java.lang.String assigneeId, java.lang.String candidateGroups, java.lang.String candidateUsers)`

#### Parameters

- `assigneeId` (`java.lang.String`)
- `candidateGroups` (`java.lang.String`)
- `candidateUsers` (`java.lang.String`)

## Methods

### `public static io.casehub.work.api.AssignmentDecision assignTo(java.lang.String id)`

Pre-assign to a specific actor; candidate fields unchanged.

#### Parameters

- `id` (`java.lang.String`)

### `public java.lang.String assigneeId()`

### `public java.lang.String candidateGroups()`

### `public java.lang.String candidateUsers()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public boolean isNoOp()`

True when this decision makes no changes to the WorkItem.

### `public static io.casehub.work.api.AssignmentDecision narrowCandidates(java.lang.String groups, java.lang.String users)`

Narrow the candidate pool without pre-assigning.

#### Parameters

- `groups` (`java.lang.String`)
- `users` (`java.lang.String`)

### `public static io.casehub.work.api.AssignmentDecision noChange()`

No changes — pool open, claim-first behaviour.

### `public final java.lang.String toString()`
