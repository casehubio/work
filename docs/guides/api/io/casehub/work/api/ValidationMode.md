# io.casehub.work.api.ValidationMode

**Package:** `io.casehub.work.api`

**Kind:** `enum`

## Enum Constants

### `PERMISSIVE` (`io.casehub.work.api.ValidationMode`)

No registry check. Default.

### `STRICT` (`io.casehub.work.api.ValidationMode`)

Reject WorkItem creation if any required capability is not in the registry.

### `WARN` (`io.casehub.work.api.ValidationMode`)

Log a warning but proceed.

## Constructors

### `private ValidationMode()`

## Methods

### `public static io.casehub.work.api.ValidationMode valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.ValidationMode[] values()`
