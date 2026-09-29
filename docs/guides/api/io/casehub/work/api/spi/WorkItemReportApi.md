# io.casehub.work.api.spi.WorkItemReportApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.ActorReportView actorPerformance(java.lang.String actorId, java.lang.String from, java.lang.String to, java.lang.String type, java.lang.String tenancyId)`

#### Parameters

- `actorId` (`java.lang.String`)
- `from` (`java.lang.String`)
- `to` (`java.lang.String`)
- `type` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.QueueHealthReportView queueHealth(java.lang.String type, java.lang.String priority, java.lang.String tenancyId)`

#### Parameters

- `type` (`java.lang.String`)
- `priority` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.SlaBreachReportView slaBreaches(java.lang.String from, java.lang.String to, java.lang.String type, java.lang.String priority, java.lang.String tenancyId)`

#### Parameters

- `from` (`java.lang.String`)
- `to` (`java.lang.String`)
- `type` (`java.lang.String`)
- `priority` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.ThroughputReportView throughput(java.lang.String from, java.lang.String to, java.lang.String groupBy, java.lang.String tenancyId)`

#### Parameters

- `from` (`java.lang.String`)
- `to` (`java.lang.String`)
- `groupBy` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
