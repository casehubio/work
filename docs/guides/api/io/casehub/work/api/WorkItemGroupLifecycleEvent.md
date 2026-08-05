# io.casehub.work.api.WorkItemGroupLifecycleEvent

**Package:** `io.casehub.work.api`

**Kind:** `class`

Fires when a multi-instance WorkItem group changes aggregate state.

Distinct from `WorkItemLifecycleEvent` (which fires per individual WorkItem transition).
Consumers subscribe to this for group-level outcomes: dashboards, notifications, CaseHub routing.
The `callerRef` is echoed from the parent WorkItem so CaseHub can route outcomes
without a query.

## Fields

### `callerRef` (`java.lang.String`)

### `completedCount` (`int`)

### `groupId` (`java.util.UUID`)

### `groupStatus` (`io.casehub.work.api.GroupStatus`)

### `instanceCount` (`int`)

### `occurredAt` (`java.time.Instant`)

### `parentId` (`java.util.UUID`)

### `rejectedCount` (`int`)

### `requiredCount` (`int`)

### `tenancyId` (`java.lang.String`)

## Constructors

### `private WorkItemGroupLifecycleEvent(java.util.UUID parentId, java.util.UUID groupId, int instanceCount, int requiredCount, int completedCount, int rejectedCount, io.casehub.work.api.GroupStatus groupStatus, java.lang.String callerRef, java.lang.String tenancyId)`

#### Parameters

- `parentId` (`java.util.UUID`)
- `groupId` (`java.util.UUID`)
- `instanceCount` (`int`)
- `requiredCount` (`int`)
- `completedCount` (`int`)
- `rejectedCount` (`int`)
- `groupStatus` (`io.casehub.work.api.GroupStatus`)
- `callerRef` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

## Methods

### `public java.lang.String callerRef()`

#### Returns

opaque caller reference echoed from parent WorkItem for routing

### `public int completedCount()`

#### Returns

number of instances that completed successfully

### `public java.util.UUID groupId()`

#### Returns

the UUID of the multi-instance group

### `public io.casehub.work.api.GroupStatus groupStatus()`

#### Returns

the current `GroupStatus` of the group

### `public int instanceCount()`

#### Returns

total number of instances in the group

### `public java.time.Instant occurredAt()`

#### Returns

the instant when this event occurred

### `public static io.casehub.work.api.WorkItemGroupLifecycleEvent of(java.util.UUID parentId, java.util.UUID groupId, int instanceCount, int requiredCount, int completedCount, int rejectedCount, io.casehub.work.api.GroupStatus groupStatus, java.lang.String callerRef, java.lang.String tenancyId)`

Factory method for creating a WorkItemGroupLifecycleEvent.

#### Parameters

- `parentId` (`java.util.UUID`) — the UUID of the parent WorkItem
- `groupId` (`java.util.UUID`) — the UUID of the multi-instance group
- `instanceCount` (`int`) — total number of instances in the group
- `requiredCount` (`int`) — threshold count needed for resolution
- `completedCount` (`int`) — number of instances that completed successfully
- `rejectedCount` (`int`) — number of instances that were rejected
- `groupStatus` (`io.casehub.work.api.GroupStatus`) — the current `GroupStatus`
- `callerRef` (`java.lang.String`) — opaque caller reference echoed from parent WorkItem
- `tenancyId` (`java.lang.String`) — the tenancy ID from the parent WorkItem

#### Returns

a new WorkItemGroupLifecycleEvent

### `public java.util.UUID parentId()`

#### Returns

the UUID of the parent WorkItem

### `public int rejectedCount()`

#### Returns

number of instances that were rejected

### `public int requiredCount()`

#### Returns

threshold count needed for group resolution

### `public java.lang.String tenancyId()`

#### Returns

the tenancy ID from the parent WorkItem
