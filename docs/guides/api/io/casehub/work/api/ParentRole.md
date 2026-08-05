# io.casehub.work.api.ParentRole

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Role of the parent WorkItem in a multi-instance group.

## Enum Constants

### `COORDINATOR` (`io.casehub.work.api.ParentRole`)

Parent is a coordinator placeholder — does not appear in any inbox, purely structural.

### `PARTICIPANT` (`io.casehub.work.api.ParentRole`)

Parent is a participant — real work item with its own inbox presence and lifecycle.

## Constructors

### `private ParentRole()`

## Methods

### `public static io.casehub.work.api.ParentRole valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.ParentRole[] values()`
