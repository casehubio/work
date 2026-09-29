# io.casehub.work.api.WorkItemRootView

**Package:** `io.casehub.work.api`

**Kind:** `record`

Projection of a root WorkItem (parentId IS NULL) enriched with
aggregate stats for the threaded inbox view.

## Fields

### `childCount` (`int`)

### `completedCount` (`java.lang.Integer`)

### `groupStatus` (`io.casehub.work.api.GroupStatus`)

### `requiredCount` (`java.lang.Integer`)

### `workItem` (`io.casehub.work.api.WorkItem`)

## Record Components

### `childCount` (`int`)

### `completedCount` (`java.lang.Integer`)

### `groupStatus` (`io.casehub.work.api.GroupStatus`)

### `requiredCount` (`java.lang.Integer`)

### `workItem` (`io.casehub.work.api.WorkItem`)

## Constructors

### `public WorkItemRootView(io.casehub.work.api.WorkItem workItem, int childCount, java.lang.Integer completedCount, java.lang.Integer requiredCount, io.casehub.work.api.GroupStatus groupStatus)`

#### Parameters

- `workItem` (`io.casehub.work.api.WorkItem`)
- `childCount` (`int`)
- `completedCount` (`java.lang.Integer`)
- `requiredCount` (`java.lang.Integer`)
- `groupStatus` (`io.casehub.work.api.GroupStatus`)

## Methods

### `public int childCount()`

### `public java.lang.Integer completedCount()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.work.api.GroupStatus groupStatus()`

### `public final int hashCode()`

### `public java.lang.Integer requiredCount()`

### `public final java.lang.String toString()`

### `public io.casehub.work.api.WorkItem workItem()`
