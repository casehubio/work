# io.casehub.work.api.spi.WorkItemStore

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

KV-native store SPI for `WorkItem` persistence.

<p>
Replaces the SQL-shaped `WorkItemRepository` with a store interface
that separates primary-key operations (`.put`, `.get`) from
query operations (`.scan`) using the `WorkItemQuery` value object.
Backends translate `WorkItemQuery` to their native query language.

<p>
<strong>CDI backend activation (four-tier priority ladder):</strong><br>
Tier 0: `@DefaultBean` (no-op fallback) — not applicable to this SPI.<br>
Tier 1: `@ApplicationScoped` (JPA/SQL, default) — `casehub-work` runtime.<br>
Tier 2: `@Alternative @Priority(1)` (MongoDB) — `casehub-work-persistence-mongodb`.<br>
Tier 3: `@Alternative @Priority(100)` (in-memory, ephemeral) — `casehub-work-persistence-memory`.<br>
Adding a backend module to the classpath activates it automatically — no consumer changes.
See the platform
<a href="https://github.com/casehubio/garden/blob/main/docs/protocols/universal/persistence-backend-cdi-priority.md">persistence-backend-cdi-priority</a>
protocol.

## Methods

### `public default long countByParentAndAssignee(java.util.UUID parentId, java.lang.String assigneeId, java.util.UUID excludeId)`

Count instances in a multi-instance group assigned to the given claimant,
excluding the WorkItem being claimed.
Returns 0 by default — override in JPA store.

#### Parameters

- `parentId` (`java.util.UUID`) — the UUID of the parent WorkItem whose group is being checked
- `assigneeId` (`java.lang.String`) — the claimant to check for existing held instances
- `excludeId` (`java.util.UUID`) — the WorkItem being claimed (excluded from the count)

#### Returns

the number of other instances in the group already held by the claimant

### `public default long countByQuery(io.casehub.work.api.WorkItemQuery query)`

Counts WorkItems matching the query without hydrating entities.
Default implementation delegates to `.scan` — JPA overrides
with a native COUNT query for efficiency.

#### Parameters

- `query` (`io.casehub.work.api.WorkItemQuery`)

### `public default java.util.Optional<io.casehub.work.api.WorkItem> findActiveByCallerRef(java.lang.String callerRef)`

Find a non-terminal (active) WorkItem by its caller reference, returning the most
recently created match.

<p>
`callerRef` is the opaque string set by the caller when the WorkItem was created
(e.g. `"case:{caseId`/pi:{planItemId}"}). Returns only non-terminal WorkItems —
useful for idempotent creation checks where a duplicate WorkItem in a terminal status
should be ignored.

<p>
The default implementation performs a linear scan via `.scanAll()` — override in
JPA/SQL stores for an indexed query on `callerRef` with a status filter.

#### Parameters

- `callerRef` (`java.lang.String`) — the caller reference to look up; must not be `null`

#### Returns

an `Optional` containing the most recently created active WorkItem, or empty

### `public default java.util.Optional<io.casehub.work.api.WorkItem> findByCallerRef(java.lang.String callerRef)`

Find a WorkItem by its caller reference, returning the most recently created match.

<p>
`callerRef` is the opaque string set by the caller when the WorkItem was created
(e.g. `"case:{caseId`/pi:{planItemId}"}). When multiple WorkItems share the same
callerRef, the most recently created is returned.

<p>
The default implementation performs a linear scan via `.scanAll()` — override in
JPA/SQL stores for an indexed query.

#### Parameters

- `callerRef` (`java.lang.String`) — the caller reference to look up; must not be `null`

#### Returns

an `Optional` containing the most recently created matching WorkItem, or empty if not found

### `public default java.util.Optional<io.casehub.work.api.WorkItem> findByOrigin(java.lang.String originServiceId, java.util.UUID originWorkItemId)`

Find a shadow WorkItem by its origin coordinates — the service ID and WorkItem ID
on the owning service. Used by the federation receiver to locate existing shadows
for upsert.

<p>
The default implementation performs a linear scan via `.scanAll()` — override in
JPA/SQL stores for an indexed query on `(origin_service_id, origin_work_item_id)`.

#### Parameters

- `originServiceId` (`java.lang.String`) — the owning service's identifier
- `originWorkItemId` (`java.util.UUID`) — the WorkItem ID on the owning service

#### Returns

an `Optional` containing the matching shadow, or empty if not found

### `public default java.util.List<io.casehub.work.api.WorkItem> findByParentId(java.util.UUID parentId)`

Find all child WorkItems by parent ID.

#### Parameters

- `parentId` (`java.util.UUID`) — the parent WorkItem UUID

#### Returns

list of child WorkItems; may be empty

### `public default java.util.List<io.casehub.work.api.WorkItem> findByParentIdExcludingStatuses(java.util.UUID parentId, java.util.List<io.casehub.work.api.WorkItemStatus> excludeStatuses)`

Find child WorkItems by parent ID, excluding those in the given terminal statuses.
Used by multi-instance group policy for cancelling/suspending remaining children.

#### Parameters

- `parentId` (`java.util.UUID`) — the parent WorkItem UUID
- `excludeStatuses` (`java.util.List<io.casehub.work.api.WorkItemStatus>`) — statuses to exclude from results

#### Returns

list of matching child WorkItems; may be empty

### `public default java.util.List<io.casehub.work.api.WorkItem> findByParentIdWithStatuses(java.util.UUID parentId, java.util.List<io.casehub.work.api.WorkItemStatus> statuses)`

Find child WorkItems by parent ID matching any of the given statuses.

#### Parameters

- `parentId` (`java.util.UUID`) — the parent WorkItem UUID
- `statuses` (`java.util.List<io.casehub.work.api.WorkItemStatus>`) — statuses to include in results

#### Returns

list of matching child WorkItems; may be empty

### `public abstract java.util.Optional<io.casehub.work.api.WorkItem> get(java.util.UUID id)`

Retrieve a WorkItem by its primary key.

#### Parameters

- `id` (`java.util.UUID`) — the UUID primary key

#### Returns

an `Optional` containing the work item, or empty if not found

### `public abstract io.casehub.work.api.WorkItem put(io.casehub.work.api.WorkItem workItem)`

Persist or update a WorkItem and return the saved instance.

<p><strong>Optimistic concurrency control (OCC) contract:</strong>
Production implementations <em>must</em> provide OCC on update. Two concurrent
`put()` calls on the same WorkItem must produce exactly one success and
one `jakarta.persistence.OptimisticLockException` (or equivalent). The
service layer relies on this for claim atomicity — without OCC, two nodes
racing to claim the same WorkItem would both succeed, producing a double-claim.

<ul>
  <li>JPA: `@Version` on `WorkItem.version` — Hibernate enforces OCC.
  <li>MongoDB: version-checked `replaceOne` — application-level OCC.
  <li>InMemory: no OCC (shared references, `ConcurrentHashMap`). Acceptable
      because this backend is test-only (`@Alternative @Priority(100)`).
</ul>

#### Parameters

- `workItem` (`io.casehub.work.api.WorkItem`) — the work item to persist; must not be `null`

#### Returns

the persisted work item

#### Throws

- `RuntimeException` — if the item was concurrently modified (OCC violation)

### `public abstract java.util.List<io.casehub.work.api.WorkItem> scan(io.casehub.work.api.WorkItemQuery query)`

Scan WorkItems matching the given query criteria.

<p>
Assignment fields in the query are combined with OR logic; all other fields
are combined with AND logic. A `null` field imposes no constraint.

<p>
Use `WorkItemQuery` static factories for common patterns:
inbox, expired,
claimExpired,
byLabelPattern.

#### Parameters

- `query` (`io.casehub.work.api.WorkItemQuery`) — the query criteria; must not be `null`

#### Returns

list of matching work items; may be empty, never null

### `public default java.util.List<io.casehub.work.api.WorkItem> scanAll()`

Return all WorkItems — for admin and monitoring use only.
Equivalent to `scan(WorkItemQuery.all())`.

#### Returns

unordered list of all persisted work items

### `public default java.util.List<io.casehub.work.api.WorkItemRootView> scanRoots(java.lang.String assignee, java.lang.String candidateUser, java.util.List<java.lang.String> candidateGroups)`

Return root WorkItems (parentId IS NULL) visible to the caller, enriched with aggregate stats.

<p>Visibility is an OR across all provided dimensions:
<ul>
  <li>`assignee` — matches `assigneeId = assignee`
  <li>`candidateUser` — matches `candidateUsers CONTAINS candidateUser`
  <li>`candidateGroups` — matches any group in the comma-separated `candidateGroups` field
</ul>

<p>Any null parameter is skipped; if all are null/empty, returns an empty list.

#### Parameters

- `assignee` (`java.lang.String`) — the worker currently assigned; may be null
- `candidateUser` (`java.lang.String`) — a user eligible to claim the WorkItem; may be null
- `candidateGroups` (`java.util.List<java.lang.String>`) — the groups to check visibility for; may be null or empty

#### Returns

list of root WorkItems enriched with child stats; never null

### `public default io.casehub.work.api.WorkItemSummary summaryByQuery(io.casehub.work.api.WorkItemQuery query, java.time.Instant now)`

#### Parameters

- `query` (`io.casehub.work.api.WorkItemQuery`)
- `now` (`java.time.Instant`)
