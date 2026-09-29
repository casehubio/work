# io.casehub.work.api.spi.WorkItemApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.WorkItemView addLabel(java.util.UUID workItemId, java.lang.String path, java.lang.String appliedBy, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `path` (`java.lang.String`)
- `appliedBy` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView clone(java.util.UUID workItemId, java.lang.String title, java.lang.String createdBy, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `title` (`java.lang.String`)
- `createdBy` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView create(io.casehub.work.api.WorkItemCreateRequest request, java.lang.String tenancyId)`

#### Parameters

- `request` (`io.casehub.work.api.WorkItemCreateRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemWithAuditView getById(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.WorkItemRootView> inbox(java.lang.String assignee, java.util.List<java.lang.String> candidateGroups, java.lang.String candidateUser, io.casehub.work.api.WorkItemStatus status, io.casehub.work.api.WorkItemPriority priority, java.lang.String type, java.lang.Boolean followUp, java.lang.String outcome, java.lang.String tenancyId)`

#### Parameters

- `assignee` (`java.lang.String`)
- `candidateGroups` (`java.util.List<java.lang.String>`)
- `candidateUser` (`java.lang.String`)
- `status` (`io.casehub.work.api.WorkItemStatus`)
- `priority` (`io.casehub.work.api.WorkItemPriority`)
- `type` (`java.lang.String`)
- `followUp` (`java.lang.Boolean`)
- `outcome` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItemSummary inboxSummary(java.lang.String assignee, java.util.List<java.lang.String> candidateGroups, java.lang.String candidateUser, io.casehub.work.api.WorkItemStatus status, io.casehub.work.api.WorkItemPriority priority, java.lang.String type, java.lang.String tenancyId)`

#### Parameters

- `assignee` (`java.lang.String`)
- `candidateGroups` (`java.util.List<java.lang.String>`)
- `candidateUser` (`java.lang.String`)
- `status` (`io.casehub.work.api.WorkItemStatus`)
- `priority` (`io.casehub.work.api.WorkItemPriority`)
- `type` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemPage listAll(io.casehub.work.api.WorkItemStatus status, io.casehub.work.api.WorkItemPriority priority, java.lang.String label, java.lang.String outcome, java.lang.String tenancyId, java.lang.Integer offset, java.lang.Integer limit)`

#### Parameters

- `status` (`io.casehub.work.api.WorkItemStatus`)
- `priority` (`io.casehub.work.api.WorkItemPriority`)
- `label` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `offset` (`java.lang.Integer`)
- `limit` (`java.lang.Integer`)

### `public abstract io.casehub.work.api.view.WorkItemView removeLabel(java.util.UUID workItemId, java.lang.String path, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `path` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract Multi<io.casehub.work.api.WorkItemLifecycleEvent> streamEvents(java.util.UUID workItemId, java.lang.String type, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `type` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract Multi<io.casehub.work.api.WorkItemLifecycleEvent> streamWorkItemEvents(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)
