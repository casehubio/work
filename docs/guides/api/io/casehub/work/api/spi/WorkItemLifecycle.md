# io.casehub.work.api.spi.WorkItemLifecycle

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract void cancel(java.util.UUID id, java.lang.String actorId, java.lang.String reason)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `reason` (`java.lang.String`)

### `public default void complete(java.util.UUID id, java.lang.String actorId, java.lang.String resolution, java.lang.String outcome)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `resolution` (`java.lang.String`)
- `outcome` (`java.lang.String`)

### `public abstract void complete(java.util.UUID id, java.lang.String actorId, java.lang.String resolution, java.lang.String outcome, java.lang.String rationale, java.lang.String planRef)`

#### Parameters

- `id` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `resolution` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `rationale` (`java.lang.String`)
- `planRef` (`java.lang.String`)
