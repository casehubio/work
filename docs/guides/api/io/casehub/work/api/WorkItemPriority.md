# io.casehub.work.api.WorkItemPriority

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Priority level of a WorkItem, used to drive inbox ordering and
escalation policy thresholds. Aligns with Linear's priority vocabulary.

## Enum Constants

### `HIGH` (`io.casehub.work.api.WorkItemPriority`)

High priority — handle before MEDIUM and LOW items.

### `LOW` (`io.casehub.work.api.WorkItemPriority`)

Low priority — attend to when time permits.

### `MEDIUM` (`io.casehub.work.api.WorkItemPriority`)

Medium priority — standard processing order.

### `URGENT` (`io.casehub.work.api.WorkItemPriority`)

Urgent priority — requires immediate attention; handle before all others.

## Constructors

### `private WorkItemPriority()`

## Methods

### `public static io.casehub.work.api.WorkItemPriority valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.WorkItemPriority[] values()`
