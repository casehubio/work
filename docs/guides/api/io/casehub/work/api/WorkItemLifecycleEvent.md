# io.casehub.work.api.WorkItemLifecycleEvent

**Package:** `io.casehub.work.api`

**Kind:** `class`

## Fields

### `LEDGER_ENTRY_ID_SETTER` (`io.casehub.work.api.LedgerEntryIdSetter`)

Returns the SPI accessor for setting ledgerEntryId on an event. Only
`LedgerEventCapture` should call this — the public event API stays immutable.

### `actor` (`java.lang.String`)

### `assigneeId` (`java.lang.String`)

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.lang.String`)

### `detail` (`java.lang.String`)

### `ledgerEntryId` (`java.util.UUID`)

### `occurredAt` (`java.time.Instant`)

### `outcome` (`java.lang.String`)

### `planRef` (`java.lang.String`)

### `rationale` (`java.lang.String`)

### `resolution` (`java.lang.String`)

### `sourceUri` (`java.lang.String`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `subject` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

### `type` (`java.lang.String`)

### `types` (`java.util.List<java.lang.String>`)

### `workItem` (`io.casehub.work.api.WorkItem`)

### `workItemId` (`java.util.UUID`)

## Constructors

### `private WorkItemLifecycleEvent(java.lang.String type, java.lang.String sourceUri, java.lang.String subject, java.util.UUID workItemId, io.casehub.work.api.WorkItemStatus status, java.time.Instant occurredAt, java.lang.String actor, java.lang.String detail, java.lang.String rationale, java.lang.String planRef, java.lang.String outcome, java.lang.String tenancyId, java.lang.String callerRef, java.lang.String assigneeId, java.lang.String resolution, java.lang.String candidateGroups, java.util.List<java.lang.String> types, io.casehub.work.api.WorkItem workItem)`

#### Parameters

- `type` (`java.lang.String`)
- `sourceUri` (`java.lang.String`)
- `subject` (`java.lang.String`)
- `workItemId` (`java.util.UUID`)
- `status` (`io.casehub.work.api.WorkItemStatus`)
- `occurredAt` (`java.time.Instant`)
- `actor` (`java.lang.String`)
- `detail` (`java.lang.String`)
- `rationale` (`java.lang.String`)
- `planRef` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `callerRef` (`java.lang.String`)
- `assigneeId` (`java.lang.String`)
- `resolution` (`java.lang.String`)
- `candidateGroups` (`java.lang.String`)
- `types` (`java.util.List<java.lang.String>`)
- `workItem` (`io.casehub.work.api.WorkItem`)

## Methods

### `public java.lang.String actor()`

Who triggered the transition.

### `public java.lang.String assigneeId()`

The assigneeId from the WorkItem (who is assigned to complete this work).
For wire-reconstructed events, this is stored independently; for local events,
it is read from the embedded workItem entity.

### `public java.lang.String callerRef()`

The callerRef from the WorkItem (external correlation identifier).
For wire-reconstructed events, this is stored independently; for local events,
it is read from the embedded workItem entity.

### `public java.lang.String candidateGroups()`

The candidateGroups from the WorkItem (comma-separated list of eligible groups).
For wire-reconstructed events, this is stored independently; for local events,
it is read from the embedded workItem entity.

### `public java.util.Map<java.lang.String,java.lang.Object> context()`

### `public java.lang.String detail()`

Optional detail payload (e.g. resolution text, rejection reason).

### `public io.casehub.work.api.WorkEventType eventType()`

### `public static io.casehub.work.api.WorkItemLifecycleEvent fromWire(java.lang.String type, java.lang.String sourceUri, java.lang.String subject, java.util.UUID workItemId, io.casehub.work.api.WorkItemStatus status, java.time.Instant occurredAt, java.lang.String actor, java.lang.String detail, java.lang.String rationale, java.lang.String planRef, java.lang.String outcome, java.lang.String tenancyId, java.lang.String callerRef, java.lang.String assigneeId, java.lang.String resolution, java.lang.String candidateGroups, java.util.List<java.lang.String> types, java.util.UUID ledgerEntryId)`

Reconstructs a lifecycle event from wire-format fields — for use by distributed
broadcaster implementations that receive serialised events from other nodes.

<p>
The `workItem` entity is `null` on the receiving node. This is intentional:
the SSE endpoint serialises only the scalar fields (workItem is `@JsonIgnore`),
so SSE clients receive identical output regardless of whether the event originated
locally or was reconstructed from the wire. Callers must not invoke `.workItem()`
or `.context()` on wire-reconstructed events.

#### Parameters

- `type` (`java.lang.String`)
- `sourceUri` (`java.lang.String`)
- `subject` (`java.lang.String`)
- `workItemId` (`java.util.UUID`)
- `status` (`io.casehub.work.api.WorkItemStatus`)
- `occurredAt` (`java.time.Instant`)
- `actor` (`java.lang.String`)
- `detail` (`java.lang.String`)
- `rationale` (`java.lang.String`)
- `planRef` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `callerRef` (`java.lang.String`)
- `assigneeId` (`java.lang.String`)
- `resolution` (`java.lang.String`)
- `candidateGroups` (`java.lang.String`)
- `types` (`java.util.List<java.lang.String>`)
- `ledgerEntryId` (`java.util.UUID`)

### `public java.util.UUID ledgerEntryId()`

The work-ledger entry ID for this lifecycle transition. Set by
`LedgerEventCapture` after persisting the entry — null if
the ledger module is absent or this event was constructed without one.

### `public static io.casehub.work.api.LedgerEntryIdSetter ledgerEntryIdSetter()`

### `public java.time.Instant occurredAt()`

When this event was created.

### `public static io.casehub.work.api.WorkItemLifecycleEvent of(java.lang.String eventName, io.casehub.work.api.WorkItem workItem, java.lang.String actor, java.lang.String detail)`

#### Parameters

- `eventName` (`java.lang.String`)
- `workItem` (`io.casehub.work.api.WorkItem`)
- `actor` (`java.lang.String`)
- `detail` (`java.lang.String`)

### `public static io.casehub.work.api.WorkItemLifecycleEvent of(java.lang.String eventName, io.casehub.work.api.WorkItem workItem, java.lang.String actor, java.lang.String detail, java.lang.String rationale, java.lang.String planRef)`

#### Parameters

- `eventName` (`java.lang.String`)
- `workItem` (`io.casehub.work.api.WorkItem`)
- `actor` (`java.lang.String`)
- `detail` (`java.lang.String`)
- `rationale` (`java.lang.String`)
- `planRef` (`java.lang.String`)

### `public java.lang.String outcome()`

The named outcome recorded at completion (e.g. `"approved"`, `"rejected"`).

<p>
Null in two distinct cases:
<ol>
<li>Non-completion events (CREATED, ASSIGNED, etc.) — no outcome is applicable.</li>
<li>System-initiated completions via `completeFromSystem()` (e.g. multi-instance
    threshold reached by `MultiInstanceGroupPolicy`) — no human-assigned outcome.</li>
</ol>
Observers that switch on outcome must handle null explicitly.

### `public java.lang.String planRef()`

The policy/procedure version that governed this action (nullable).

### `public java.lang.String rationale()`

The actor's stated basis for the decision (nullable).

### `public io.casehub.work.api.WorkItemRef ref()`

### `public java.lang.String resolution()`

The resolution JSON from the WorkItem.
For wire-reconstructed events, this is stored independently; for local events,
it is read from the embedded workItem entity.

### `public java.lang.String sourceUri()`

The CloudEvents source URI (e.g. "/workitems/{id}").
Use `.workItem()` for the WorkItem itself.

### `public io.casehub.work.api.WorkItemStatus status()`

The status AFTER the transition.

### `public java.lang.String subject()`

The CloudEvents subject — the WorkItem UUID as a string.

### `public java.lang.String tenancyId()`

The tenancy ID of the WorkItem this event belongs to.
Server-side only — never serialised to SSE clients.

### `public java.lang.String type()`

The CloudEvents type string (e.g. "io.casehub.work.workitem.created").

### `public java.util.List<java.lang.String> types()`

The types from the WorkItem (path-based type classification).
For wire-reconstructed events, this is stored independently; for local events,
it is read from the embedded workItem entity.

### `public io.casehub.work.api.WorkItem workItem()`

### `public java.util.UUID workItemId()`

The affected WorkItem's UUID.
