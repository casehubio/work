# io.casehub.work.api.spi.WorkItemCreator

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.WorkItemRef create(io.casehub.work.api.WorkItemCreateRequest request)`

#### Parameters

- `request` (`io.casehub.work.api.WorkItemCreateRequest`)

### `public default io.casehub.work.api.WorkItemRef createMultiInstance(io.casehub.work.api.WorkItemCreateRequest parentRequest, io.casehub.work.api.MultiInstanceConfig config)`

#### Parameters

- `parentRequest` (`io.casehub.work.api.WorkItemCreateRequest`)
- `config` (`io.casehub.work.api.MultiInstanceConfig`)

### `public abstract java.util.Optional<io.casehub.work.api.WorkItemRef> findActiveByCallerRef(java.lang.String callerRef)`

Find a non-terminal (active) WorkItem by its caller reference.

<p>When multiple active WorkItems share the same `callerRef`, returns the
<strong>most recently created</strong> match. Terminal WorkItems are excluded.

#### Parameters

- `callerRef` (`java.lang.String`) — the caller reference to look up; must not be `null`

#### Returns

the most recently created active WorkItem with the given callerRef, or empty

### `public abstract java.util.Optional<io.casehub.work.api.WorkItemRef> findByCallerRef(java.lang.String callerRef)`

Find a WorkItem by its caller reference.

<p>When multiple WorkItems share the same `callerRef` (e.g. first expires,
second created and completed), returns the <strong>most recently created</strong>
match. Callers should not assume callerRef uniqueness across the lifecycle.

#### Parameters

- `callerRef` (`java.lang.String`) — the caller reference to look up; must not be `null`

#### Returns

the most recently created WorkItem with the given callerRef, or empty

### `public default java.util.List<java.lang.String> findChildApprovers(java.util.UUID parentId)`

#### Parameters

- `parentId` (`java.util.UUID`)

### `public abstract void obsoleteByCallerRef(java.lang.String callerRef)`

Mark the most recent WorkItem with the given callerRef as OBSOLETE.

<p>Idempotent: if the WorkItem is already in a terminal state, this is a no-op.
If no WorkItem matches the callerRef, this is a no-op.

#### Parameters

- `callerRef` (`java.lang.String`) — the caller reference identifying the WorkItem to obsolete
