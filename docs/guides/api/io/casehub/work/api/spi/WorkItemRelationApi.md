# io.casehub.work.api.spi.WorkItemRelationApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.WorkItemRelationView addRelation(java.util.UUID workItemId, io.casehub.work.api.view.AddRelationRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `body` (`io.casehub.work.api.view.AddRelationRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.WorkItemView> children(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract void deleteRelation(java.util.UUID workItemId, java.util.UUID relationId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `relationId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.WorkItemRelationView> listIncoming(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.WorkItemRelationView> listOutgoing(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView parent(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)
