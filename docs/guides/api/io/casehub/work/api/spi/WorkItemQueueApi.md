# io.casehub.work.api.spi.WorkItemQueueApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.CreateQueueResult create(io.casehub.work.api.view.CreateQueueRequest request, java.lang.String tenancyId)`

#### Parameters

- `request` (`io.casehub.work.api.view.CreateQueueRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract void delete(java.util.UUID queueId, java.lang.String tenancyId)`

#### Parameters

- `queueId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.QueueHealthMetricView> health(java.lang.String tenancyId)`

#### Parameters

- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.QueueSummaryView> list(java.lang.String tenancyId)`

#### Parameters

- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.WorkItemView> query(java.util.UUID queueId, java.lang.String tenancyId)`

#### Parameters

- `queueId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.WorkItemSummary summary(java.util.UUID queueId, java.lang.String tenancyId)`

#### Parameters

- `queueId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.QueueTrendView trend(java.util.UUID queueId, java.lang.String period, java.lang.String tenancyId)`

#### Parameters

- `queueId` (`java.util.UUID`)
- `period` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
