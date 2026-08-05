# io.casehub.work.api.MultiInstanceConfig

**Package:** `io.casehub.work.api`

**Kind:** `record`

## Fields

### `allowSameAssignee` (`boolean`)

### `assignmentStrategyName` (`java.lang.String`)

### `explicitAssignees` (`java.util.List<java.lang.String>`)

### `instanceCount` (`int`)

### `onThresholdReached` (`io.casehub.work.api.OnThresholdReached`)

### `parentRole` (`io.casehub.work.api.ParentRole`)

### `requiredCount` (`int`)

## Record Components

### `allowSameAssignee` (`boolean`)

### `assignmentStrategyName` (`java.lang.String`)

### `explicitAssignees` (`java.util.List<java.lang.String>`)

### `instanceCount` (`int`)

### `onThresholdReached` (`io.casehub.work.api.OnThresholdReached`)

### `parentRole` (`io.casehub.work.api.ParentRole`)

### `requiredCount` (`int`)

## Constructors

### `public MultiInstanceConfig(int instanceCount, int requiredCount, io.casehub.work.api.ParentRole parentRole, java.lang.String assignmentStrategyName, io.casehub.work.api.OnThresholdReached onThresholdReached, boolean allowSameAssignee, java.util.List<java.lang.String> explicitAssignees)`

#### Parameters

- `instanceCount` (`int`)
- `requiredCount` (`int`)
- `parentRole` (`io.casehub.work.api.ParentRole`)
- `assignmentStrategyName` (`java.lang.String`)
- `onThresholdReached` (`io.casehub.work.api.OnThresholdReached`)
- `allowSameAssignee` (`boolean`)
- `explicitAssignees` (`java.util.List<java.lang.String>`)

## Methods

### `public boolean allowSameAssignee()`

### `public java.lang.String assignmentStrategyName()`

### `public java.lang.String effectiveAssignmentStrategyName()`

### `public io.casehub.work.api.OnThresholdReached effectiveOnThresholdReached()`

### `public io.casehub.work.api.ParentRole effectiveParentRole()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.List<java.lang.String> explicitAssignees()`

### `public final int hashCode()`

### `public int instanceCount()`

### `public io.casehub.work.api.OnThresholdReached onThresholdReached()`

### `public io.casehub.work.api.ParentRole parentRole()`

### `public int requiredCount()`

### `public final java.lang.String toString()`

### `public java.util.List<java.lang.String> validate()`

Returns a list of validation error messages; empty = valid.
