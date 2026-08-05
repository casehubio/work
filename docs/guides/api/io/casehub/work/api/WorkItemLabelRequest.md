# io.casehub.work.api.WorkItemLabelRequest

**Package:** `io.casehub.work.api`

**Kind:** `record`

Value object carrying label data for WorkItem creation.
Pure Java — no JPA annotations, no HTTP concerns.

## Fields

### `appliedBy` (`java.lang.String`)

### `path` (`java.lang.String`)

### `persistence` (`io.casehub.work.api.LabelPersistence`)

## Record Components

### `appliedBy` (`java.lang.String`)

### `path` (`java.lang.String`)

### `persistence` (`io.casehub.work.api.LabelPersistence`)

## Constructors

### `public WorkItemLabelRequest(java.lang.String path, io.casehub.work.api.LabelPersistence persistence, java.lang.String appliedBy)`

#### Parameters

- `path` (`java.lang.String`)
- `persistence` (`io.casehub.work.api.LabelPersistence`)
- `appliedBy` (`java.lang.String`)

## Methods

### `public java.lang.String appliedBy()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String path()`

### `public io.casehub.work.api.LabelPersistence persistence()`

### `public final java.lang.String toString()`
