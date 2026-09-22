# io.casehub.work.api.spi.WorkItemScheduleApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.ScheduleView create(io.casehub.work.api.view.CreateScheduleRequest request, java.lang.String tenancyId)`

#### Parameters

- `request` (`io.casehub.work.api.view.CreateScheduleRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract void delete(java.util.UUID scheduleId, java.lang.String tenancyId)`

#### Parameters

- `scheduleId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.ScheduleView get(java.util.UUID scheduleId, java.lang.String tenancyId)`

#### Parameters

- `scheduleId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.ScheduleView> list(java.lang.String tenancyId)`

#### Parameters

- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.ScheduleView setActive(java.util.UUID scheduleId, io.casehub.work.api.view.SetActiveRequest request, java.lang.String tenancyId)`

#### Parameters

- `scheduleId` (`java.util.UUID`)
- `request` (`io.casehub.work.api.view.SetActiveRequest`)
- `tenancyId` (`java.lang.String`)
