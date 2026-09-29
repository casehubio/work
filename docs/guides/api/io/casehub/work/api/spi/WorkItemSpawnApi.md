# io.casehub.work.api.spi.WorkItemSpawnApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract void cancelGroup(java.util.UUID workItemId, java.util.UUID groupId, boolean cancelChildren, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `groupId` (`java.util.UUID`)
- `cancelChildren` (`boolean`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.SpawnGroupSummaryView> listSpawnGroups(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.SpawnResultView spawn(java.util.UUID workItemId, io.casehub.work.api.view.SpawnBodyRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `body` (`io.casehub.work.api.view.SpawnBodyRequest`)
- `tenancyId` (`java.lang.String`)
