# io.casehub.work.api.view.InstancesView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `completedCount` (`int`)

### `groupId` (`java.util.UUID`)

### `groupStatus` (`java.lang.String`)

### `instanceCount` (`int`)

### `instances` (`java.util.List<io.casehub.work.api.view.WorkItemView>`)

### `parentId` (`java.util.UUID`)

### `rejectedCount` (`int`)

### `requiredCount` (`int`)

## Record Components

### `completedCount` (`int`)

### `groupId` (`java.util.UUID`)

### `groupStatus` (`java.lang.String`)

### `instanceCount` (`int`)

### `instances` (`java.util.List<io.casehub.work.api.view.WorkItemView>`)

### `parentId` (`java.util.UUID`)

### `rejectedCount` (`int`)

### `requiredCount` (`int`)

## Constructors

### `public InstancesView(java.util.UUID parentId, java.util.UUID groupId, int instanceCount, int requiredCount, int completedCount, int rejectedCount, java.lang.String groupStatus, java.util.List<io.casehub.work.api.view.WorkItemView> instances)`

#### Parameters

- `parentId` (`java.util.UUID`)
- `groupId` (`java.util.UUID`)
- `instanceCount` (`int`)
- `requiredCount` (`int`)
- `completedCount` (`int`)
- `rejectedCount` (`int`)
- `groupStatus` (`java.lang.String`)
- `instances` (`java.util.List<io.casehub.work.api.view.WorkItemView>`)

## Methods

### `public int completedCount()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.UUID groupId()`

### `public java.lang.String groupStatus()`

### `public final int hashCode()`

### `public int instanceCount()`

### `public java.util.List<io.casehub.work.api.view.WorkItemView> instances()`

### `public java.util.UUID parentId()`

### `public int rejectedCount()`

### `public int requiredCount()`

### `public final java.lang.String toString()`
