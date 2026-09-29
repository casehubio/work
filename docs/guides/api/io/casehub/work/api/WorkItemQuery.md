# io.casehub.work.api.WorkItemQuery

**Package:** `io.casehub.work.api`

**Kind:** `class`

KV-native query criteria for `WorkItemStore.scan`.

<p>
Replaces the individual query methods on the former `WorkItemRepository`
(`findInbox`, `findExpired`, `findByLabelPattern`, etc.)
with a single composable value object. Backends translate this to their
native query language (SQL, MongoDB aggregation, Redis set operations, etc.).

<h2>Semantics</h2>
<p>
Assignment fields (`assigneeId`, `candidateGroups`,
`candidateUserId`) are combined with <b>OR</b> logic — an item matches
if it satisfies any one of the non-null assignment criteria.

<p>
All other fields (`status`, `statusIn`, `priority`,
`type`, `followUpBefore`, `expiresAtOrBefore`,
`claimDeadlineOrBefore`, `labelPattern`) are combined with
<b>AND</b> logic on top of the assignment match. A `null` value means
"no constraint on this dimension".

<h2>Common patterns</h2>

<pre>
// Inbox for alice in finance-team, HIGH priority only
WorkItemQuery.inbox("alice", List.of("finance-team"), null)
        .toBuilder().priority(WorkItemPriority.HIGH).build();

// All WorkItems with an expired completion deadline
WorkItemQuery.expired(Instant.now());

// WorkItems matching a label pattern
WorkItemQuery.byLabelPattern("legal/**");
</pre>

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

### `private WorkItemQuery(io.casehub.work.api.WorkItemQuery.Builder b)`

#### Parameters

- `b` (`io.casehub.work.api.WorkItemQuery.Builder`)

## Methods

### `public static io.casehub.work.api.WorkItemQuery all()`

No constraints — returns all WorkItems. Admin use only.

### `public java.lang.String assigneeId()`

Returns the direct assignee filter, or `null` if not constrained.

#### Returns

assignee id filter value

### `public static io.casehub.work.api.WorkItemQuery.Builder builder()`

Returns a new empty builder.

#### Returns

a fresh builder instance

### `public static io.casehub.work.api.WorkItemQuery byLabelPattern(java.lang.String pattern)`

Items with at least one label matching the given pattern (exact / `*` / `**`).

#### Parameters

- `pattern` (`java.lang.String`) — the label pattern to match against; must not be null

#### Returns

query matching work items with a label satisfying the pattern

### `public java.util.List<java.lang.String> candidateGroups()`

Returns the candidate groups filter, or `null` if not constrained.

#### Returns

candidate groups filter value

### `public java.lang.String candidateUserId()`

Returns the candidate user id filter, or `null` if not constrained.

#### Returns

candidate user id filter value

### `public java.time.Instant claimDeadlineOrBefore()`

Returns the claim-deadline-or-before filter, or `null` if not constrained.

#### Returns

claim-deadline-or-before instant

### `public static io.casehub.work.api.WorkItemQuery claimExpired(java.time.Instant now)`

Items whose `claimDeadline` is on or before `now` and whose
status is `WorkItemStatus.PENDING`.

#### Parameters

- `now` (`java.time.Instant`) — the reference instant to compare against `claimDeadline`

#### Returns

query matching pending work items past their claim deadline

### `public static io.casehub.work.api.WorkItemQuery expired(java.time.Instant now)`

Items whose `expiresAt` is on or before `now` and whose
status is one of the active (non-terminal) statuses.

#### Parameters

- `now` (`java.time.Instant`) — the reference instant to compare against `expiresAt`

#### Returns

query matching expired active work items

### `public java.time.Instant expiresAtOrBefore()`

Returns the expires-at-or-before filter, or `null` if not constrained.

#### Returns

expires-at-or-before instant

### `public java.time.Instant followUpBefore()`

Returns the follow-up-before filter, or `null` if not constrained.

#### Returns

follow-up-before instant

### `public static io.casehub.work.api.WorkItemQuery inbox(java.lang.String assigneeId, java.util.List<java.lang.String> candidateGroups, java.lang.String candidateUserId)`

Inbox query: items visible to the given actor via any assignment dimension.
All three parameters are nullable; at least one should be non-null.

#### Parameters

- `assigneeId` (`java.lang.String`) — the direct assignee identifier; may be `null`
- `candidateGroups` (`java.util.List<java.lang.String>`) — groups the actor belongs to; may be `null` or empty
- `candidateUserId` (`java.lang.String`) — user listed in candidateUsers; may be `null`

#### Returns

query matching items visible via any assignment dimension

### `public java.lang.String labelPattern()`

Returns the label pattern filter, or `null` if not constrained.

#### Returns

label pattern string

### `public java.lang.String outcome()`

Returns the exact outcome filter, or `null` if not constrained.

#### Returns

outcome filter value

### `public io.casehub.work.api.WorkItemPriority priority()`

Returns the priority filter, or `null` if not constrained.

#### Returns

priority filter value

### `public io.casehub.work.api.WorkItemStatus status()`

Returns the exact status filter, or `null` if not constrained.

#### Returns

status filter value

### `public java.util.List<io.casehub.work.api.WorkItemStatus> statusIn()`

Returns the status-in filter, or `null` if not constrained.

#### Returns

list of acceptable statuses

### `public java.lang.String tenancyId()`

Returns the tenant id filter, or `null` if not constrained.
When `null`, store implementations fall back to the current
principal's tenant.

#### Returns

tenancy id filter value

### `public io.casehub.work.api.WorkItemQuery.Builder toBuilder()`

Returns a builder pre-populated with this query's values for incremental modification.

### `public java.lang.String type()`

Returns the type filter, or `null` if not constrained.
<p>
Matches WorkItems whose `types` set contains a type equal to
or descended from this query value. For example, `type("compliance")`
matches a WorkItem with type `"compliance/audit"`.

#### Returns

type filter value (path-based ancestor match)
