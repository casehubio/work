# io.casehub.work.api.spi.WorkItemLifecycleApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.WorkItemView acceptDelegation(java.util.UUID workItemId, java.lang.String claimant, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `claimant` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView cancel(java.util.UUID workItemId, java.lang.String actor, io.casehub.work.api.view.CancelRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `body` (`io.casehub.work.api.view.CancelRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView claim(java.util.UUID workItemId, java.lang.String claimant, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `claimant` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView compensate(java.util.UUID workItemId, io.casehub.work.api.view.CompensateRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `body` (`io.casehub.work.api.view.CompensateRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView complete(java.util.UUID workItemId, java.lang.String actor, io.casehub.work.api.view.CompleteRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `body` (`io.casehub.work.api.view.CompleteRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView declineDelegation(java.util.UUID workItemId, java.lang.String actor, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView delegate(java.util.UUID workItemId, java.lang.String actor, io.casehub.work.api.view.DelegateRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `body` (`io.casehub.work.api.view.DelegateRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView escalate(java.util.UUID workItemId, java.lang.String actor, io.casehub.work.api.view.EscalateRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `body` (`io.casehub.work.api.view.EscalateRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView extend(java.util.UUID workItemId, java.lang.String actor, io.casehub.work.api.view.ExtendRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `body` (`io.casehub.work.api.view.ExtendRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView fault(java.util.UUID workItemId, io.casehub.work.api.view.FaultRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `body` (`io.casehub.work.api.view.FaultRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView obsolete(java.util.UUID workItemId, io.casehub.work.api.view.ObsoleteRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `body` (`io.casehub.work.api.view.ObsoleteRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView reject(java.util.UUID workItemId, java.lang.String actor, io.casehub.work.api.view.RejectRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `body` (`io.casehub.work.api.view.RejectRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView release(java.util.UUID workItemId, java.lang.String actor, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView resume(java.util.UUID workItemId, java.lang.String actor, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView start(java.util.UUID workItemId, java.lang.String actor, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView suspend(java.util.UUID workItemId, java.lang.String actor, io.casehub.work.api.view.SuspendRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `body` (`io.casehub.work.api.view.SuspendRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView updateDeadline(java.util.UUID workItemId, java.lang.String actor, io.casehub.work.api.view.UpdateDeadlineRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `actor` (`java.lang.String`)
- `body` (`io.casehub.work.api.view.UpdateDeadlineRequest`)
- `tenancyId` (`java.lang.String`)
