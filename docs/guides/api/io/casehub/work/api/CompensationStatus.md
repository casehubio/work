# io.casehub.work.api.CompensationStatus

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Denormalized compensation state of a WorkItem whose effects are being reversed.

## Enum Constants

### `COMPENSATED` (`io.casehub.work.api.CompensationStatus`)

The compensating WorkItem has completed — this WorkItem's effects are reversed.

### `COMPENSATING` (`io.casehub.work.api.CompensationStatus`)

A compensating WorkItem has been created and is in progress.

### `NONE` (`io.casehub.work.api.CompensationStatus`)

No compensation activity.

## Constructors

### `private CompensationStatus()`

## Methods

### `public static io.casehub.work.api.CompensationStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.CompensationStatus[] values()`
