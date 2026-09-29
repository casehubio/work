# io.casehub.work.api.WorkItemQuery.Builder

**Package:** `io.casehub.work.api`

**Kind:** `class`

Builder for `WorkItemQuery`.

## Fields

### `assigneeId` (`java.lang.String`)

### `candidateGroups` (`java.util.List<java.lang.String>`)

### `candidateUserId` (`java.lang.String`)

### `claimDeadlineOrBefore` (`java.time.Instant`)

### `expiresAtOrBefore` (`java.time.Instant`)

### `followUpBefore` (`java.time.Instant`)

### `labelPattern` (`java.lang.String`)

### `outcome` (`java.lang.String`)

### `priority` (`io.casehub.work.api.WorkItemPriority`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `statusIn` (`java.util.List<io.casehub.work.api.WorkItemStatus>`)

### `tenancyId` (`java.lang.String`)

### `type` (`java.lang.String`)

## Constructors

### `public Builder()`

## Methods

### `public io.casehub.work.api.WorkItemQuery.Builder assigneeId(java.lang.String v)`

Sets the assignee id constraint.

#### Parameters

- `v` (`java.lang.String`) — the assignee id; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery build()`

Builds the `WorkItemQuery`.

#### Returns

a new immutable query instance

### `public io.casehub.work.api.WorkItemQuery.Builder candidateGroups(java.util.List<java.lang.String> v)`

Sets the candidate groups constraint.

#### Parameters

- `v` (`java.util.List<java.lang.String>`) — the candidate groups; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder candidateUserId(java.lang.String v)`

Sets the candidate user id constraint.

#### Parameters

- `v` (`java.lang.String`) — the candidate user id; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder claimDeadlineOrBefore(java.time.Instant v)`

Sets the claim-deadline-or-before constraint.

#### Parameters

- `v` (`java.time.Instant`) — the instant; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder expiresAtOrBefore(java.time.Instant v)`

Sets the expires-at-or-before constraint.

#### Parameters

- `v` (`java.time.Instant`) — the instant; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder followUpBefore(java.time.Instant v)`

Sets the follow-up-before constraint.

#### Parameters

- `v` (`java.time.Instant`) — the instant; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder labelPattern(java.lang.String v)`

Sets the label pattern constraint.

#### Parameters

- `v` (`java.lang.String`) — the pattern; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder outcome(java.lang.String v)`

Sets the exact outcome constraint.

#### Parameters

- `v` (`java.lang.String`) — the outcome string; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder priority(io.casehub.work.api.WorkItemPriority v)`

Sets the priority constraint.

#### Parameters

- `v` (`io.casehub.work.api.WorkItemPriority`) — the priority; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder status(io.casehub.work.api.WorkItemStatus v)`

Sets the exact status constraint.

#### Parameters

- `v` (`io.casehub.work.api.WorkItemStatus`) — the status; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder statusIn(java.util.List<io.casehub.work.api.WorkItemStatus> v)`

Sets the status-in constraint.

#### Parameters

- `v` (`java.util.List<io.casehub.work.api.WorkItemStatus>`) — the list of acceptable statuses; `null` means unconstrained

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder tenancyId(java.lang.String v)`

Sets the tenant id constraint. When `null`, store implementations
fall back to the current principal's tenant.

#### Parameters

- `v` (`java.lang.String`) — the tenancy id; `null` means use current principal

#### Returns

this builder

### `public io.casehub.work.api.WorkItemQuery.Builder type(java.lang.String v)`

Sets the type constraint. Matches WorkItems whose `types` set
contains a type equal to or descended from this value.

#### Parameters

- `v` (`java.lang.String`) — the type path; `null` means unconstrained

#### Returns

this builder
