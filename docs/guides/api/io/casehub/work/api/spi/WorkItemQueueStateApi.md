# io.casehub.work.api.spi.WorkItemQueueStateApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.WorkItemView pickup(java.util.UUID workItemId, java.lang.String claimant, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `claimant` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.RelinquishableResult setRelinquishable(java.util.UUID workItemId, io.casehub.work.api.view.RelinquishableRequest request, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `request` (`io.casehub.work.api.view.RelinquishableRequest`)
- `tenancyId` (`java.lang.String`)
