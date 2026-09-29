# io.casehub.work.api.view.IssueLinkView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `externalRef` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `linkedAt` (`java.time.Instant`)

### `linkedBy` (`java.lang.String`)

### `status` (`java.lang.String`)

### `title` (`java.lang.String`)

### `trackerType` (`java.lang.String`)

### `url` (`java.lang.String`)

### `workItemId` (`java.util.UUID`)

## Record Components

### `externalRef` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `linkedAt` (`java.time.Instant`)

### `linkedBy` (`java.lang.String`)

### `status` (`java.lang.String`)

### `title` (`java.lang.String`)

### `trackerType` (`java.lang.String`)

### `url` (`java.lang.String`)

### `workItemId` (`java.util.UUID`)

## Constructors

### `public IssueLinkView(java.util.UUID id, java.util.UUID workItemId, java.lang.String trackerType, java.lang.String externalRef, java.lang.String title, java.lang.String url, java.lang.String status, java.time.Instant linkedAt, java.lang.String linkedBy)`

#### Parameters

- `id` (`java.util.UUID`)
- `workItemId` (`java.util.UUID`)
- `trackerType` (`java.lang.String`)
- `externalRef` (`java.lang.String`)
- `title` (`java.lang.String`)
- `url` (`java.lang.String`)
- `status` (`java.lang.String`)
- `linkedAt` (`java.time.Instant`)
- `linkedBy` (`java.lang.String`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String externalRef()`

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.time.Instant linkedAt()`

### `public java.lang.String linkedBy()`

### `public java.lang.String status()`

### `public java.lang.String title()`

### `public final java.lang.String toString()`

### `public java.lang.String trackerType()`

### `public java.lang.String url()`

### `public java.util.UUID workItemId()`
