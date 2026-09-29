# io.casehub.work.api.spi.WorkItemOperations

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.WorkItem acceptDelegation(java.util.UUID id, java.lang.String claimantId)`

#### Parameters

- `id` (`java.util.UUID`)
- `claimantId` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem addLabel(java.util.UUID workItemId, java.lang.String path, java.lang.String appliedBy)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `path` (`java.lang.String`)
- `appliedBy` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem cancel(java.util.UUID id, java.lang.String actorId, java.lang.String reason)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem cancelFromSystem(java.util.UUID id, java.lang.String actorId, java.lang.String reason)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem claim(java.util.UUID id, java.lang.String claimantId)`

#### Parameters

- `id` (`java.util.UUID`)
- `claimantId` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem clone(java.util.UUID sourceId, java.lang.String titleOverride, java.lang.String createdBy)`

#### Parameters

- `sourceId` (`java.util.UUID`)
- `titleOverride` (`java.lang.String`)
- `createdBy` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem compensate(java.util.UUID originalId, io.casehub.work.api.WorkItemCreateRequest request, java.lang.String triggeredBy, java.lang.String reason)`

#### Parameters

- `originalId` (`java.util.UUID`)
- `request` (`io.casehub.work.api.WorkItemCreateRequest`)
- `triggeredBy` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem complete(java.util.UUID id, java.lang.String actorId, java.lang.String resolution, java.lang.String outcome)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `resolution` (`java.lang.String`)
- `outcome` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem complete(java.util.UUID id, java.lang.String actorId, java.lang.String resolution, java.lang.String outcome, java.lang.String rationale, java.lang.String planRef)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `resolution` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `rationale` (`java.lang.String`)
- `planRef` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem completeFromSystem(java.util.UUID id, java.lang.String actorId, java.lang.String resolution)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `resolution` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem create(io.casehub.work.api.WorkItemCreateRequest request)`

#### Parameters

- `request` (`io.casehub.work.api.WorkItemCreateRequest`)

### `public default io.casehub.work.api.WorkItem createInTenantContext(java.lang.String tenancyId, io.casehub.work.api.WorkItemCreateRequest request)`

Create a work item within an explicitly established tenant context.

<p>Activates a request scope for the given `tenancyId`, creates the work item,
and tears down the context. Use this from async/background paths where no request
scope is active (e.g., qhorus afterCompletion callbacks, inbound bridges).

#### Parameters

- `tenancyId` (`java.lang.String`) — the tenant identity to establish — must be derived from the
                 authenticated security context, never from user-supplied input
- `request` (`io.casehub.work.api.WorkItemCreateRequest`) — the work item to create

#### Returns

the created work item

### `public abstract io.casehub.work.api.WorkItem declineDelegation(java.util.UUID id, java.lang.String actorId)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem delegate(java.util.UUID id, java.lang.String actorId, java.lang.String toAssigneeId, io.casehub.work.api.DeclineTarget declineTarget)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `toAssigneeId` (`java.lang.String`)
- `declineTarget` (`io.casehub.work.api.DeclineTarget`)

### `public abstract io.casehub.work.api.WorkItem escalate(java.util.UUID id, java.lang.String actor, java.lang.String targetGroup, java.lang.String reason)`

#### Parameters

- `id` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `targetGroup` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem extend(java.util.UUID id, java.time.Instant newExpiresAt, java.lang.String actorId)`

#### Parameters

- `id` (`java.util.UUID`)
- `newExpiresAt` (`java.time.Instant`)
- `actorId` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem fault(java.util.UUID id, java.lang.String systemActorId, java.lang.String errorDetail)`

#### Parameters

- `id` (`java.util.UUID`)
- `systemActorId` (`java.lang.String`)
- `errorDetail` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem faultFromSystem(java.util.UUID id, java.lang.String actorId, java.lang.String errorDetail)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `errorDetail` (`java.lang.String`)

### `public abstract java.util.Optional<io.casehub.work.api.WorkItem> findActiveByCallerRef(java.lang.String callerRef)`

#### Parameters

- `callerRef` (`java.lang.String`)

### `public abstract java.util.Optional<io.casehub.work.api.WorkItem> findByCallerRef(java.lang.String callerRef)`

#### Parameters

- `callerRef` (`java.lang.String`)

### `public abstract java.util.Optional<io.casehub.work.api.WorkItem> findById(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.work.api.WorkItem> findChildrenByParentId(java.util.UUID parentId)`

#### Parameters

- `parentId` (`java.util.UUID`)

### `public abstract io.casehub.work.api.WorkItem markCompensated(java.util.UUID originalId)`

#### Parameters

- `originalId` (`java.util.UUID`)

### `public abstract io.casehub.work.api.WorkItem obsolete(java.util.UUID id, java.lang.String triggeredBy, java.lang.String reason)`

#### Parameters

- `id` (`java.util.UUID`)
- `triggeredBy` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem obsoleteFromSystem(java.util.UUID id, java.lang.String triggeredBy, java.lang.String reason)`

#### Parameters

- `id` (`java.util.UUID`)
- `triggeredBy` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem reject(java.util.UUID id, java.lang.String actorId, java.lang.String reason, java.lang.String outcome)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `reason` (`java.lang.String`)
- `outcome` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem reject(java.util.UUID id, java.lang.String actorId, java.lang.String reason, java.lang.String outcome, java.lang.String rationale)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `reason` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `rationale` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem rejectFromSystem(java.util.UUID id, java.lang.String actorId, java.lang.String reason)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem release(java.util.UUID id, java.lang.String actorId)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem removeLabel(java.util.UUID workItemId, java.lang.String path)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `path` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem resume(java.util.UUID id, java.lang.String actorId)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.WorkItem> scan(io.casehub.work.api.WorkItemQuery query)`

#### Parameters

- `query` (`io.casehub.work.api.WorkItemQuery`)

### `public abstract io.casehub.work.api.WorkItem start(java.util.UUID id, java.lang.String actorId)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem suspend(java.util.UUID id, java.lang.String actorId, java.lang.String reason)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItem updateDeadline(java.util.UUID id, java.time.Instant newDeadline, java.lang.String actorId)`

#### Parameters

- `id` (`java.util.UUID`)
- `newDeadline` (`java.time.Instant`)
- `actorId` (`java.lang.String`)
